package kr.co.ircp.cms.infra.kosha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * KOSHA 산재 사례 API 단건 응답 항목.
 *
 * <p>REQ-SAFETY-001-D-4: 공공데이터포털 OSHIS API 응답 구조 매핑.
 * 알 수 없는 필드는 무시한다 (API 버전 변경 대응).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KoshaIncidentItem(
        /** 재해 발생 연도 */
        @JsonProperty("accdntYear") String accidentYear,
        /** 업종 코드 (KSIC) */
        @JsonProperty("indutyCd") String industryCode,
        /** 재해 유형 코드 */
        @JsonProperty("accdntTypeCd") String incidentTypeCode,
        /** 재해 유형명 */
        @JsonProperty("accdntTypeNm") String incidentTypeName,
        /** 사망자 수 */
        @JsonProperty("dthpcnt") Integer deathCount,
        /** 부상자 수 */
        @JsonProperty("injurypcnt") Integer injuryCount,
        /** 재해 발생 일자 (yyyyMMdd) */
        @JsonProperty("accdntOccrrncYmd") String occurredDate,
        /** 재해 발생 장소 */
        @JsonProperty("accdntOccrrncPlc") String location,
        /** 재해 개요 */
        @JsonProperty("accdntCn") String summary,
        /** 원인 분석 */
        @JsonProperty("accdntCauseCn") String detailedCause,
        /** 예방 대책 */
        @JsonProperty("prevnMsrCn") String preventionLesson,
        /** 출처 URL */
        @JsonProperty("srcUrl") String sourceUrl
) {}
