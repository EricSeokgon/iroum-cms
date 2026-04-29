package kr.co.ircp.cms.integration.auth;

import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.repository.PasswordHistoryMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PasswordHistoryMapper 통합 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-010 — 비밀번호 재사용 금지 이력 저장 및 조회 검증.
 */
@Transactional
class PasswordHistoryMapperIT extends AbstractIntegrationTest {

    @Autowired
    private PasswordHistoryMapper passwordHistoryMapper;

    @Autowired
    private UserMapper userMapper;

    private long userId;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .username("ph_it_user")
                .email("ph_it@example.com")
                .passwordHash("$2a$12$initial_hash____________________")
                .name("비밀번호이력테스트")
                .status(UserStatus.ACTIVE)
                .failCount(0)
                .passwordChangedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(user);
        userId = user.getId();
    }

    @Test
    void insert_persistsHistory() {
        // when
        passwordHistoryMapper.insert(userId, "$2a$12$hash_v1_____________________", Instant.now());

        // then
        List<String> hashes = passwordHistoryMapper.findRecentHashes(userId, 5);
        assertThat(hashes).hasSize(1);
        assertThat(hashes.get(0)).isEqualTo("$2a$12$hash_v1_____________________");
    }

    @Test
    void findRecentHashes_returnsLastN_orderedDesc() {
        // given — 3개 삽입 (시간 간격 확보)
        Instant t1 = Instant.now().minusSeconds(10);
        Instant t2 = Instant.now().minusSeconds(5);
        Instant t3 = Instant.now();
        passwordHistoryMapper.insert(userId, "$2a$12$hash_old____________________", t1);
        passwordHistoryMapper.insert(userId, "$2a$12$hash_mid____________________", t2);
        passwordHistoryMapper.insert(userId, "$2a$12$hash_new____________________", t3);

        // when — 최근 2개만
        List<String> hashes = passwordHistoryMapper.findRecentHashes(userId, 2);

        // then — 최신 순으로 2개
        assertThat(hashes).hasSize(2);
        assertThat(hashes.get(0)).isEqualTo("$2a$12$hash_new____________________");
        assertThat(hashes.get(1)).isEqualTo("$2a$12$hash_mid____________________");
    }
}
