"""Pydantic 스키마 — Spring Jackson camelCase 직렬화 계약과 1:1 매칭.

SPEC-CMS-ML-SERVICE-001.

[보안] 모든 요청 스키마는 ``extra="forbid"`` 로 설정하여
정의되지 않은(잠재적 PII) 필드를 거부한다. 회사명/대표자명/사업자번호 등
식별정보는 어떤 요청에도 포함될 수 없다.
"""

from __future__ import annotations

from typing import Dict, List, Optional

from pydantic import BaseModel, ConfigDict, Field

# ── 공통 설정 ────────────────────────────────────────────────
_FORBID = ConfigDict(extra="forbid")


# ── 예측 계열 요청 (PII 없음: 4개 필드 한정) ──────────────────
class PredictionRequest(BaseModel):
    """성장단계/위험도/시뮬레이션 공통 요청.

    Spring ``GrowthStageRequest``/``RiskScoreRequest``/``SimulationRequest``
    (camelCase) 와 매칭. PII를 포함하지 않는다.
    """

    model_config = _FORBID

    ksicCode: str
    capitalAmount: int
    foundingYear: int
    revenueAmount: int


# ── 성장단계 ──────────────────────────────────────────────────
class GrowthStageResponse(BaseModel):
    """Java ``GrowthStageResponse`` 레코드와 매칭."""

    model_config = ConfigDict(extra="forbid")

    stage: str
    entryProbabilities: Dict[str, float]
    confidence: float
    modelVersion: str


# ── 위험도 ────────────────────────────────────────────────────
class RiskFactor(BaseModel):
    """Java ``RiskScoreResponse.RiskFactor`` 레코드와 매칭."""

    model_config = ConfigDict(extra="forbid")

    name: str
    contribution: float


class RiskScoreResponse(BaseModel):
    """Java ``RiskScoreResponse`` 레코드와 매칭."""

    model_config = ConfigDict(extra="forbid")

    defaultProbability: float
    riskGrade: str
    topFactors: List[RiskFactor]
    modelVersion: str


# ── 시뮬레이션 ────────────────────────────────────────────────
class ProjectionPoint(BaseModel):
    """Java ``SimulationResponse.ProjectionPoint`` 레코드와 매칭."""

    model_config = ConfigDict(extra="forbid")

    year: int
    stage: str
    entryProbabilities: Dict[str, float]


class SimulationResponse(BaseModel):
    """Java ``SimulationResponse`` 레코드와 매칭."""

    model_config = ConfigDict(extra="forbid")

    projection: List[ProjectionPoint]
    modelVersion: str


# ── 정책 매칭 ────────────────────────────────────────────────
class CompanyProfile(BaseModel):
    """정책 매칭용 회사 프로파일.

    [보안] PII 미포함 — ksic_code/employee_count/growth_stage/region_code/
    annual_revenue 5개 키만 허용한다. 그 외 키는 거부(extra=forbid).
    """

    model_config = ConfigDict(extra="forbid")

    ksic_code: str
    employee_count: int
    growth_stage: str
    region_code: str
    annual_revenue: int


class PolicyMatchRequest(BaseModel):
    """Java ``MlPolicyMatchRequest`` 레코드와 매칭."""

    model_config = ConfigDict(extra="forbid")

    companyProfile: CompanyProfile
    queryText: str
    candidatePolicyIds: List[int]
    topK: int = Field(default=3, ge=1)


class MatchExplanation(BaseModel):
    """Java ``MlMatchExplanation`` 레코드와 매칭."""

    model_config = ConfigDict(extra="forbid")

    matchedTerms: List[str]
    rationale: str


class MatchItem(BaseModel):
    """Java ``MlMatchItem`` 레코드와 매칭."""

    model_config = ConfigDict(extra="forbid")

    policyId: int
    semanticScore: float
    explanation: MatchExplanation


class PolicyMatchResponse(BaseModel):
    """Java ``MlPolicyMatchResponse`` 레코드와 매칭."""

    model_config = ConfigDict(extra="forbid")

    matches: List[MatchItem]
    modelName: str
    modelVersion: str


# ── 임베딩 ────────────────────────────────────────────────────
class EmbedRequest(BaseModel):
    """Java ``EmbedRequest`` 레코드와 매칭.

    [보안] text 만 허용. 식별정보 절대 불가(extra=forbid).
    """

    model_config = ConfigDict(extra="forbid")

    text: str


class EmbedResponse(BaseModel):
    """Java ``EmbedResponse`` 레코드와 매칭. vector 는 정확히 384차원."""

    model_config = ConfigDict(extra="forbid")

    vector: List[float]


# ── RAG ───────────────────────────────────────────────────────
class RagContextItem(BaseModel):
    """Java ``RagContextItem`` 레코드와 매칭.

    [보안] id/title/content 만 허용. 사용자 식별정보 불가(extra=forbid).
    """

    model_config = ConfigDict(extra="forbid")

    id: int
    title: str
    content: str


class RagRequest(BaseModel):
    """Java ``RagRequest`` 레코드와 매칭."""

    model_config = ConfigDict(extra="forbid")

    question: str
    contexts: List[RagContextItem]


class RagSource(BaseModel):
    """Java ``RagResponse.Source`` 레코드와 매칭."""

    model_config = ConfigDict(extra="forbid")

    id: int
    relevance: float


class RagResponse(BaseModel):
    """Java ``RagResponse`` 레코드와 매칭. qualityScore 는 NULL 허용."""

    model_config = ConfigDict(extra="forbid")

    answer: str
    sources: List[RagSource]
    qualityScore: Optional[int] = None


# ── 헬스 ──────────────────────────────────────────────────────
class HealthResponse(BaseModel):
    """Java ``MlHealthResponse`` 레코드와 매칭."""

    model_config = ConfigDict(extra="forbid")

    status: str
    loadedModels: List[str]
