package kr.co.ircp.cms.domain.system.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 추이 시계열 항목 DTO.
 *
 * <p>REQ-SYSTEM-002-D — 30일 일별 방문/페이지뷰/오류 추이
 * // @MX:NOTE: [AUTO] record → class 변환 — MyBatis DefaultObjectFactory가 setter 기반으로 프로퍼티를 설정하므로
 * // record는 사용 불가. Lombok @Data로 getter/setter 제공.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendItemResponse {
    private LocalDate date;
    private Integer visits;
    private Integer pageViews;
    private Integer errors;
}
