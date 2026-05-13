package kr.co.ircp.cms.domain.security.pii.rotation;

/**
 * pii_key_rotation_log INSERT 전용 holder.
 *
 * <p>MyBatis 가 keyProperty="id" 를 통해 자동 생성 PK 를 setter 로 주입한다.
 * record 가 아닌 가변 클래스로 둔 이유 — MyBatis 의 useGeneratedKeys 가
 * setter 호출에 의존하기 때문 (record 는 final field 라 주입 불가).
 *
 * <p>외부 호출자는 본 클래스를 직접 사용하지 않고
 * {@link PiiKeyRotationMapper#insertRotationLog(int, int)} default method 를 사용한다.
 */
public class RotationLogInsert {

    private Long id;
    private final int oldVersion;
    private final int newVersion;

    public RotationLogInsert(int oldVersion, int newVersion) {
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getOldVersion() {
        return oldVersion;
    }

    public int getNewVersion() {
        return newVersion;
    }
}
