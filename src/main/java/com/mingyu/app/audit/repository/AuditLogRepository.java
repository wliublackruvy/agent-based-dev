package com.mingyu.app.audit.repository;

// Implements 4.会员审计功能

import com.mingyu.app.audit.model.AuditEventType;
import com.mingyu.app.audit.model.AuditLogEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    List<AuditLogEntity> findByUserIdAndEventTimeBetweenOrderByEventTimeAsc(Long userId, Instant from, Instant to);

    List<AuditLogEntity> findByUserIdAndEventTimeBetweenAndEventTypeInOrderByEventTimeAsc(
            Long userId, Instant from, Instant to, Collection<AuditEventType> eventTypes);
}