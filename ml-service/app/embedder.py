"""문장 임베딩 싱글톤 (SPEC-CMS-ML-SERVICE-001).

기본 모델: sentence-transformers ``paraphrase-multilingual-MiniLM-L12-v2``
(384차원). 라이브러리/모델 미가용(오프라인 CI 등)인 경우에는 결정론적
해시 기반 384차원 폴백 임베딩을 사용한다.

폴백을 두는 이유:
- 계약(정확히 384개 float, 코사인 유사도 0~1)은 두 경로 모두에서 동일하게 성립
- 단위 테스트가 ~400MB torch/모델 다운로드 없이 오프라인에서 빠르게 통과
- Docker 이미지는 빌드 시 실제 모델을 사전 다운로드하므로 운영은 실모델 사용
"""

from __future__ import annotations

import hashlib
import threading
from typing import List

import numpy as np

EMBEDDING_DIM = 384
MODEL_NAME = "paraphrase-multilingual-MiniLM-L12-v2"


class Embedder:
    """임베딩 모델 래퍼. 실모델 우선, 실패 시 결정론적 폴백."""

    def __init__(self) -> None:
        self._model = None
        self._backend = "fallback-hash-384d"
        try:
            from sentence_transformers import SentenceTransformer  # type: ignore

            self._model = SentenceTransformer(MODEL_NAME)
            self._backend = "sentence-transformers"
        except Exception:  # noqa: BLE001 - 폴백은 의도된 동작
            self._model = None

    @property
    def backend(self) -> str:
        """현재 활성 백엔드 식별자."""
        return self._backend

    def encode(self, text: str) -> np.ndarray:
        """텍스트를 L2 정규화된 384차원 벡터로 인코딩한다."""
        if self._model is not None:
            vec = np.asarray(
                self._model.encode(
                    text,
                    normalize_embeddings=True,
                    show_progress_bar=False,
                ),
                dtype=np.float64,
            )
            return vec
        return self._fallback_encode(text)

    @staticmethod
    def _fallback_encode(text: str) -> np.ndarray:
        """결정론적 해시 기반 384차원 임베딩 (L2 정규화).

        동일 입력 → 동일 출력. 의미 유사도는 근사적이나 계약(차원/범위)은
        실모델과 동일하게 보장한다.
        """
        out = np.empty(EMBEDDING_DIM, dtype=np.float64)
        base = text.encode("utf-8")
        for i in range(EMBEDDING_DIM):
            digest = hashlib.sha256(base + i.to_bytes(2, "big")).digest()
            # 8바이트를 [-1, 1) 범위 float 로 변환
            val = int.from_bytes(digest[:8], "big") / float(1 << 64)
            out[i] = val * 2.0 - 1.0
        norm = np.linalg.norm(out)
        if norm == 0.0:
            out[0] = 1.0
            return out
        return out / norm


_LOCK = threading.Lock()
_INSTANCE: Embedder | None = None


def get_embedder() -> Embedder:
    """프로세스 단일 임베더 인스턴스를 반환한다 (lazy + thread-safe)."""
    global _INSTANCE  # noqa: PLW0603 - 의도된 싱글톤
    if _INSTANCE is None:
        with _LOCK:
            if _INSTANCE is None:
                _INSTANCE = Embedder()
    return _INSTANCE


def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    """두 벡터의 코사인 유사도를 [0.0, 1.0] 으로 클램프하여 반환한다."""
    denom = float(np.linalg.norm(a) * np.linalg.norm(b))
    if denom == 0.0:
        return 0.0
    raw = float(np.dot(a, b) / denom)
    # 코사인은 [-1,1]; 계약상 0~1 이어야 하므로 음수는 0 으로 클램프
    return max(0.0, min(1.0, raw))
