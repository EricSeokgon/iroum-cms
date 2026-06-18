package kr.co.ircp.cms.domain.notification.template.admin.repository;

import kr.co.ircp.cms.domain.notification.template.admin.entity.NotificationTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 알림 템플릿 매퍼 (notification_template).
 *
 * <p>SPEC-CMS-NOTI-EXT-001.
 */
// @MX:SPEC: SPEC-CMS-NOTI-EXT-001
@Mapper
public interface NotificationTemplateMapper {

    void insert(NotificationTemplate template);

    Optional<NotificationTemplate> findById(@Param("id") Long id);

    List<NotificationTemplate> findAll(
            @Param("isActive") Boolean isActive,
            @Param("pageOffset") int pageOffset,
            @Param("pageSize") int pageSize);

    long countAll(@Param("isActive") Boolean isActive);

    boolean existsByCodeAndLanguage(@Param("code") String code,
                                    @Param("language") String language,
                                    @Param("excludeId") Long excludeId);

    int update(NotificationTemplate template);

    int delete(@Param("id") Long id);
}
