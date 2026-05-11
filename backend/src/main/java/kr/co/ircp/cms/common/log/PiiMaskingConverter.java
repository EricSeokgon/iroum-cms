package kr.co.ircp.cms.common.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * PII 마스킹 Logback Converter — SPEC-CMS-SECURITY-PII-MASKING-001 REQ-PII-MASK-001.
 *
 * <p>logback-spring.xml의 모든 프로파일(dev/local/prod)에서 메시지를 마스킹한다.
 * dev/local 프로파일은 PatternLayout 변환자로, prod 프로파일은
 * {@link PiiMaskingProvider}를 통해 LogstashEncoder JSON 출력 시 호출된다.
 *
 * <p>마스킹 패턴 4종(REQ-PII-MASK-001):
 * <ul>
 *   <li>email: RFC 5321 → 첫 글자 + ***@***.*** (예: john@example.com → j***@***.***)</li>
 *   <li>phone(국내): 010/011/016/017/018/019-XXXX-XXXX → 010-****-XXXX</li>
 *   <li>SSN(주민등록번호): 6+7 → XXXXXX-*******</li>
 *   <li>IPv4: 4 옥텟 → XXX.XXX.***.*** (뒷자리 2 옥텟 마스킹)</li>
 * </ul>
 *
 * <p>운영 노출 위험 통제: 운영 로그에 평문 PII가 기록되어 ELK/Loki/CloudWatch
 * 백엔드로 유출되는 위험을 1차 방어한다.
 */
// @MX:NOTE: [AUTO] PiiMaskingConverter — Logback 메시지 PII 마스킹 (모든 프로파일 적용)
// @MX:SPEC: SPEC-CMS-SECURITY-PII-MASKING-001 / REQ-PII-MASK-001 — 운영 노출 통제
public class PiiMaskingConverter extends ClassicConverter {

    /**
     * email 패턴(RFC 5321 단순화).
     *
     * <p>로컬 파트 첫 글자만 캡쳐하여 보존하고 나머지는 *** 처리한다.
     */
    private static final Pattern EMAIL = Pattern.compile(
            "([A-Za-z0-9])[A-Za-z0-9._%+-]*@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /**
     * 국내 휴대전화 패턴(010/011/016/017/018/019).
     *
     * <p>가운데 4자리만 마스킹하고 통신사 식별자(앞 3자리)와 끝 4자리는 보존한다.
     * 하이픈은 선택적으로 매칭한다.
     */
    private static final Pattern PHONE_KR = Pattern.compile(
            "(01[016789])-?(\\d{3,4})-?(\\d{4})");

    /**
     * 주민등록번호 패턴(6+7 + 하이픈 필수).
     *
     * <p>앞자리(생년월일)는 보존하고 뒷자리 7자(성별 코드 포함)를 전부 마스킹한다.
     */
    private static final Pattern SSN = Pattern.compile(
            "(\\d{6})-(\\d{7})");

    /**
     * IPv4 4옥텟 패턴.
     *
     * <p>처음 2 옥텟만 보존하고 뒤 2 옥텟을 마스킹한다(네트워크 식별성과 PII 보호 균형).
     */
    private static final Pattern IPV4 = Pattern.compile(
            "\\b(\\d{1,3})\\.(\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}\\b");

    @Override
    public String convert(ILoggingEvent event) {
        return mask(event.getFormattedMessage());
    }

    /**
     * 외부에서도 호출 가능한 정적 마스킹 함수.
     *
     * <p>{@link PiiMaskingProvider}와 단위 테스트가 직접 호출할 수 있도록 노출한다.
     *
     * @param message 마스킹 대상 원본 문자열(null 허용)
     * @return 마스킹된 문자열(null 입력 시 빈 문자열)
     */
    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message == null ? "" : message;
        }
        String result = message;
        result = EMAIL.matcher(result).replaceAll("$1***@***.***");
        result = PHONE_KR.matcher(result).replaceAll("$1-****-$3");
        result = SSN.matcher(result).replaceAll("$1-*******");
        result = IPV4.matcher(result).replaceAll("$1.$2.***.***");
        return result;
    }
}
