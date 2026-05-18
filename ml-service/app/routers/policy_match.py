"""POST /ml/v1/policy-match — 정책 시맨틱 매칭.

ML 서비스는 정책 DB에 접근하지 않으므로 candidatePolicyId 기반
결정론적 정책 텍스트를 생성하여 회사 프로파일+질의문과 코사인 유사도를
산출한다. 매칭 토큰은 질의문 토큰 기준 휴리스틱으로 추출한다.
"""

from __future__ import annotations

import re
from typing import List

from fastapi import APIRouter

from app.embedder import cosine_similarity, get_embedder
from app.schemas import (
    MatchExplanation,
    MatchItem,
    PolicyMatchRequest,
    PolicyMatchResponse,
)

router = APIRouter()

MODEL_ID = "policy-match-embed-v1"
MODEL_NAME = "sentence-transformers"
MODEL_VERSION = "1.0.0"

# 정책 ID → 결정론적 키워드 풀 (정책 DB 미접근 환경에서의 텍스트 생성용)
_POLICY_THEMES = [
    "창업 지원 자금 융자",
    "청년 고용 장려 보조금",
    "기술 개발 R&D 지원",
    "수출 판로 개척 마케팅",
    "소상공인 경영 안정 지원",
    "지역 산업 육성 인프라",
    "여성 기업 우대 정책",
    "친환경 그린 전환 지원",
]

_TOKEN_RE = re.compile(r"[0-9A-Za-z가-힣]+")


def _policy_text(policy_id: int) -> str:
    """정책 ID 기반 결정론적 정책 설명 텍스트."""
    theme = _POLICY_THEMES[policy_id % len(_POLICY_THEMES)]
    return f"정책 {policy_id}: {theme} 프로그램. 중소기업 및 창업 기업 대상 지원 사업."


def _tokenize(text: str) -> List[str]:
    return [t for t in _TOKEN_RE.findall(text) if len(t) >= 2]


@router.post("/ml/v1/policy-match", response_model=PolicyMatchResponse)
def policy_match(req: PolicyMatchRequest) -> PolicyMatchResponse:
    """후보 정책별 시맨틱 점수와 매칭 근거를 반환한다."""
    profile = req.companyProfile
    company_text = (
        f"업종 {profile.ksic_code} 종업원 {profile.employee_count}명 "
        f"성장단계 {profile.growth_stage} 지역 {profile.region_code} "
        f"연매출 {profile.annual_revenue}"
    )
    query_full = f"{company_text} {req.queryText}"
    query_vec = get_embedder().encode(query_full)
    query_tokens = set(_tokenize(req.queryText))

    scored: List[MatchItem] = []
    for pid in req.candidatePolicyIds:
        ptext = _policy_text(pid)
        pvec = get_embedder().encode(ptext)
        score = round(cosine_similarity(query_vec, pvec), 4)

        policy_tokens = set(_tokenize(ptext))
        matched = sorted(query_tokens & policy_tokens)
        if not matched:
            # 교집합이 없으면 질의문 상위 토큰을 근거로 노출 (최대 3개)
            matched = sorted(query_tokens)[:3]

        scored.append(
            MatchItem(
                policyId=pid,
                semanticScore=score,
                explanation=MatchExplanation(
                    matchedTerms=matched[:5],
                    rationale="정책 키워드와 기업 성장단계 매칭",
                ),
            )
        )

    scored.sort(key=lambda m: m.semanticScore, reverse=True)
    return PolicyMatchResponse(
        matches=scored[: req.topK],
        modelName=MODEL_NAME,
        modelVersion=MODEL_VERSION,
    )
