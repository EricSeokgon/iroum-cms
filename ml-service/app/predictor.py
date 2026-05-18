"""규칙 기반 예측 로직 (SPEC-CMS-ML-SERVICE-001).

학습 데이터 없이 휴리스틱으로 성장단계/위험도/시뮬레이션을 산출한다.
모든 확률 맵은 합이 ~1.0 이 되도록 정규화한다.
"""

from __future__ import annotations

import datetime
from typing import Dict, List, Tuple

MODEL_VERSION = "rule-v1.0.0"

STAGES: Tuple[str, ...] = ("SEED", "STARTUP", "GROWTH", "EXPANSION", "MATURITY")

# 만원 단위가 아닌 원 단위 금액 기준 임계값
_50M = 50_000_000
_100M = 100_000_000
_500M = 500_000_000
_1B = 1_000_000_000
_5B = 5_000_000_000

# KSIC 대분류(앞 1자리) 기준 산업 변동성 테이블 (0.0 안정 ~ 1.0 변동)
_INDUSTRY_VOLATILITY: Dict[str, float] = {
    "A": 0.45,  # 농림어업
    "B": 0.55,  # 광업
    "C": 0.40,  # 제조업
    "D": 0.30,  # 전기/가스
    "E": 0.35,  # 수도/하수
    "F": 0.50,  # 건설업
    "G": 0.55,  # 도소매업
    "H": 0.45,  # 운수창고
    "I": 0.65,  # 숙박/음식 (변동 큼)
    "J": 0.50,  # 정보통신
    "K": 0.35,  # 금융보험
    "L": 0.40,  # 부동산
    "M": 0.45,  # 전문/과학/기술
    "N": 0.50,  # 사업시설관리
    "P": 0.30,  # 교육
    "Q": 0.30,  # 보건/사회복지
    "R": 0.60,  # 예술/스포츠/여가
    "S": 0.55,  # 기타 서비스
}
_DEFAULT_VOLATILITY = 0.50


def _current_year() -> int:
    return datetime.date.today().year


def _normalize(weights: Dict[str, float]) -> Dict[str, float]:
    """가중치 맵을 합이 1.0 이 되도록 정규화하고 소수 4자리로 반올림한다."""
    total = sum(weights.values())
    if total <= 0:
        # 균등 분포 폴백
        n = len(weights)
        return {k: round(1.0 / n, 4) for k in weights}
    normed = {k: v / total for k, v in weights.items()}
    # 반올림 후 잔차를 최대 항목에 흡수시켜 합 = 1.0 보장
    rounded = {k: round(v, 4) for k, v in normed.items()}
    drift = round(1.0 - sum(rounded.values()), 4)
    if drift != 0.0:
        top_key = max(rounded, key=rounded.get)
        rounded[top_key] = round(rounded[top_key] + drift, 4)
    return rounded


def _industry_volatility(ksic_code: str) -> float:
    if not ksic_code:
        return _DEFAULT_VOLATILITY
    return _INDUSTRY_VOLATILITY.get(ksic_code[0].upper(), _DEFAULT_VOLATILITY)


def _stage_weights(
    capital: int, founding_year: int, revenue: int, year: int
) -> Dict[str, float]:
    """5단계 각각의 적합도 가중치를 산출한다 (정규화 전).

    규칙(SPEC):
      - SEED:       업력<=2 AND capital<50M
      - STARTUP:    업력<=5 OR (capital<100M AND revenue<500M)
      - GROWTH:     capital>=100M AND revenue>=500M AND 업력>3
      - EXPANSION:  revenue>=1B AND 업력>7
      - MATURITY:   업력>10 AND revenue>=5B
    """
    age = year - founding_year
    w: Dict[str, float] = {s: 0.05 for s in STAGES}  # 베이스라인 노이즈

    if age <= 2 and capital < _50M:
        w["SEED"] += 0.80
    if age <= 5 or (capital < _100M and revenue < _500M):
        w["STARTUP"] += 0.70
    if capital >= _100M and revenue >= _500M and age > 3:
        w["GROWTH"] += 0.75
    if revenue >= _1B and age > 7:
        w["EXPANSION"] += 0.70
    if age > 10 and revenue >= _5B:
        w["MATURITY"] += 0.80

    # 어떤 규칙도 강하게 매칭되지 않으면 업력 기반 완만한 분포 부여
    if max(w.values()) <= 0.05 + 1e-9:
        if age <= 3:
            w["SEED"] += 0.30
            w["STARTUP"] += 0.40
        elif age <= 7:
            w["STARTUP"] += 0.35
            w["GROWTH"] += 0.35
        elif age <= 12:
            w["GROWTH"] += 0.35
            w["EXPANSION"] += 0.30
        else:
            w["EXPANSION"] += 0.30
            w["MATURITY"] += 0.40
    return w


def predict_growth_stage(
    ksic_code: str, capital: int, founding_year: int, revenue: int
) -> Tuple[str, Dict[str, float], float]:
    """성장단계 분류 + 5단계 확률(합 1.0) + confidence 반환."""
    year = _current_year()
    probs = _normalize(_stage_weights(capital, founding_year, revenue, year))
    stage = max(probs, key=probs.get)
    confidence = round(probs[stage], 4)
    return stage, probs, confidence


def _risk_grade(p: float) -> str:
    if p < 0.3:
        return "GREEN"
    if p < 0.5:
        return "YELLOW"
    if p < 0.7:
        return "ORANGE"
    return "RED"


def predict_risk_score(
    ksic_code: str, capital: int, founding_year: int, revenue: int
) -> Tuple[float, str, List[Tuple[str, float]]]:
    """부도확률 + 위험등급 + 상위 3개 기여요인 반환.

    요인:
      - capitalAdequacy:    자본 낮을수록 위험 ↑
      - industryVolatility: KSIC 대분류 산업 변동성
      - foundingTenure:     업력 짧을수록 위험 ↑
    """
    year = _current_year()
    age = max(0, year - founding_year)

    # 0~1 정규화된 개별 위험 지표
    capital_risk = max(0.0, min(1.0, 1.0 - (capital / float(_500M))))
    industry_risk = _industry_volatility(ksic_code)
    tenure_risk = max(0.0, min(1.0, 1.0 - (age / 15.0)))

    # 가중 결합 (자본 0.40 / 산업 0.33 / 업력 0.27)
    weights = {
        "capitalAdequacy": 0.40,
        "industryVolatility": 0.33,
        "foundingTenure": 0.27,
    }
    contributions_raw = {
        "capitalAdequacy": capital_risk * weights["capitalAdequacy"],
        "industryVolatility": industry_risk * weights["industryVolatility"],
        "foundingTenure": tenure_risk * weights["foundingTenure"],
    }
    default_prob = round(min(1.0, sum(contributions_raw.values())), 4)

    total_contrib = sum(contributions_raw.values())
    if total_contrib <= 0:
        factor_share = {k: round(1.0 / 3, 4) for k in contributions_raw}
    else:
        factor_share = {
            k: round(v / total_contrib, 4) for k, v in contributions_raw.items()
        }
    top_factors = sorted(
        factor_share.items(), key=lambda kv: kv[1], reverse=True
    )[:3]
    return default_prob, _risk_grade(default_prob), top_factors


def simulate(
    ksic_code: str, capital: int, founding_year: int, revenue: int
) -> List[Tuple[int, str, Dict[str, float]]]:
    """현재 상태 기준 +1, +3, +5 년 성장단계 투영을 반환한다.

    매년 매출/자본이 완만히 성장한다고 가정(연 +18% 매출, +10% 자본)하여
    각 목표 연도의 규칙 기반 단계 확률을 산출한다.
    """
    year = _current_year()
    projection: List[Tuple[int, str, Dict[str, float]]] = []
    for offset in (1, 3, 5):
        target_year = year + offset
        grown_revenue = int(revenue * (1.18**offset))
        grown_capital = int(capital * (1.10**offset))
        probs = _normalize(
            _stage_weights(
                grown_capital, founding_year, grown_revenue, target_year
            )
        )
        stage = max(probs, key=probs.get)
        projection.append((target_year, stage, probs))
    return projection
