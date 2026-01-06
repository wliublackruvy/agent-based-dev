package com.mingyu.app.relationship.repository;

// Implements 1.账号与关系管理
// Implements 6.开发者核心逻辑

import com.mingyu.app.relationship.model.RelationshipEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RelationshipRepository extends JpaRepository<RelationshipEntity, Long> {

    Optional<RelationshipEntity> findByBindingCode(String bindingCode);

    Optional<RelationshipEntity> findByInitiatorUserId(Long initiatorUserId);

    Optional<RelationshipEntity> findByPartnerUserId(Long partnerUserId);

    @Query("""
            SELECT r FROM RelationshipEntity r
            WHERE r.status = com.mingyu.app.relationship.model.RelationshipStatus.ACTIVE
              AND (r.initiatorUserId = :userId OR r.partnerUserId = :userId)
            """)
    Optional<RelationshipEntity> findActiveRelationshipForUser(@Param("userId") Long userId);

    @Query("""
            SELECT r FROM RelationshipEntity r
            WHERE r.status = com.mingyu.app.relationship.model.RelationshipStatus.ACTIVE
            """)
    List<RelationshipEntity> findAllActiveRelationships();
}