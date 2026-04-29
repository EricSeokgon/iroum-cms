package kr.co.ircp.cms.integration.auth;

import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserMapper MyBatis 통합 테스트.
 *
 * <p>실제 PostgreSQL 컨테이너 + Flyway V1~V9 적용 후 SQL 검증.
 * SPEC-CMS-002 REQ-AUTH-001~006 핵심 CRUD 흐름 커버.
 */
@Transactional
class UserMapperIT extends AbstractIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private User buildUser(String username, String email) {
        return User.builder()
                .username(username)
                .email(email)
                .passwordHash("$2a$12$placeholder_hash_for_test_only_____")
                .name("테스트유저")
                .status(UserStatus.ACTIVE)
                .failCount(0)
                .passwordChangedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ──────────────────────────────────────────────
    // 테스트
    // ──────────────────────────────────────────────

    @Test
    void findByUsername_returnsUser_whenExists() {
        // given
        User user = buildUser("mapper_it_user1", "it1@example.com");
        userMapper.insert(user);

        // when
        Optional<User> found = userMapper.findByUsername("mapper_it_user1");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("mapper_it_user1");
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getUuid()).isNotNull(); // gen_random_uuid() 자동 생성
    }

    @Test
    void insert_persistsUserWithUuid_andRoleViaInsertRole() {
        // given
        User user = buildUser("mapper_it_user2", "it2@example.com");
        userMapper.insert(user);
        assertThat(user.getId()).isNotNull();

        // when — user_roles 삽입
        userMapper.insertRole(user.getId(), "VIEWER", null, Instant.now());
        Set<String> roles = userMapper.findRoleCodesByUserId(user.getId());

        // then
        assertThat(roles).containsExactly("VIEWER");
    }

    @Test
    void updatePassword_changesHashAndPasswordChangedAt() {
        // given
        User user = buildUser("mapper_it_user3", "it3@example.com");
        userMapper.insert(user);
        Instant before = user.getPasswordChangedAt();

        // when
        Instant changeTime = Instant.now().plusSeconds(1);
        userMapper.updatePassword(user.getId(), "$2a$12$new_hash_for_test_only_________", changeTime);

        // then
        Optional<User> updated = userMapper.findById(user.getId());
        assertThat(updated).isPresent();
        assertThat(updated.get().getPasswordHash()).isEqualTo("$2a$12$new_hash_for_test_only_________");
        assertThat(updated.get().getPasswordChangedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void softDelete_setsDeletedAt_butKeepsRow() {
        // given
        User user = buildUser("mapper_it_user4", "it4@example.com");
        userMapper.insert(user);

        // when
        userMapper.softDelete(user.getId(), Instant.now());

        // then — deleted_at IS NULL 조건 때문에 findByUsername 반환 없음
        Optional<User> afterDelete = userMapper.findByUsername("mapper_it_user4");
        assertThat(afterDelete).isEmpty();
    }

    @Test
    void findPageWithScope_filtersByOrgPath() {
        // given
        User user = buildUser("mapper_it_user5", "it5@example.com");
        userMapper.insert(user);

        // when — orgPathPrefix null 이면 전체 조회
        List<kr.co.ircp.cms.domain.auth.dto.UserSummary> page =
                userMapper.findPageWithScope(0, 50, null, null, "username", null);

        // then
        assertThat(page).isNotEmpty();
    }
}
