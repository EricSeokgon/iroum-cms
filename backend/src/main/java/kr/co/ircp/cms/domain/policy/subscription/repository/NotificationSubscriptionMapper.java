package kr.co.ircp.cms.domain.policy.subscription.repository;

import kr.co.ircp.cms.domain.policy.subscription.entity.NotificationSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 수신 동의 매퍼.
 * REQ-POLICY-004
 */
@Mapper
public interface NotificationSubscriptionMapper {

    List<NotificationSubscription> findByUserId(@Param("userId") Long userId);

    /** UPSERT: 채널·카테고리 단위로 매번 갱신. */
    void upsert(NotificationSubscription subscription);

    /** 발송 직전 옵트아웃 검증 (이중 검증). */
    boolean isOptedIn(
            @Param("userId") Long userId,
            @Param("channel") String channel,
            @Param("category") String category
    );
}
