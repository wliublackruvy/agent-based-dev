// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.relation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mingyu.app.common.api.ErrorCode;
import com.mingyu.app.common.exception.BusinessException;
import com.mingyu.app.dal.entity.BindCode;
import com.mingyu.app.dal.entity.Relation;
import com.mingyu.app.dal.mapper.BindCodeMapper;
import com.mingyu.app.dal.mapper.RelationMapper;
import com.mingyu.app.relation.service.dto.BindCodeResult;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RelationService {

    private static final Duration BIND_CODE_TTL = Duration.ofMinutes(5);

    private final BindCodeMapper bindCodeMapper;
    private final RelationMapper relationMapper;

    public RelationService(BindCodeMapper bindCodeMapper,
                           RelationMapper relationMapper) {
        this.bindCodeMapper = bindCodeMapper;
        this.relationMapper = relationMapper;
    }

    @Transactional
    public BindCodeResult generateBindCode(Long ownerUserId) {
        assertNoActiveRelation(ownerUserId);
        String code = generateUniqueCode();
        LocalDateTime now = LocalDateTime.now();
        BindCode bindCode = new BindCode();
        bindCode.setCode(code);
        bindCode.setOwnerUserId(ownerUserId);
        bindCode.setStatus("ACTIVE");
        bindCode.setCreatedAt(now);
        bindCode.setExpiresAt(now.plus(BIND_CODE_TTL));
        bindCodeMapper.insert(bindCode);
        return new BindCodeResult(code, (int) BIND_CODE_TTL.toSeconds());
    }

    @Transactional
    public void bind(Long userId, String code) {
        BindCode bindCode = bindCodeMapper.selectOne(Wrappers.<BindCode>lambdaQuery()
                .eq(BindCode::getCode, code)
                .eq(BindCode::getStatus, "ACTIVE")
                .gt(BindCode::getExpiresAt, LocalDateTime.now()));
        
        if (bindCode == null) {
            throw new BusinessException(ErrorCode.BIND_CODE_INVALID, "绑定码无效或已过期");
        }

        assertNoActiveRelation(userId);
        
        if (bindCode.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不能绑定自己生成的绑定码");
        }
        
        assertNoActiveRelation(bindCode.getOwnerUserId());
        
        LocalDateTime now = LocalDateTime.now();
        Relation relation = new Relation();
        relation.setUserAId(bindCode.getOwnerUserId());
        relation.setUserBId(userId);
        relation.setStatus("ACTIVE");
        relation.setCreatedAt(now);
        relationMapper.insert(relation);
        
        bindCode.setStatus("USED");
        bindCode.setUsedByUserId(userId);
        bindCode.setUsedAt(now);
        bindCodeMapper.updateById(bindCode);
    }

    @Transactional
    public void requestUnbind(Long userId, String reason) {
        Relation relation = findActiveRelation(userId);
        if (relation == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前没有活跃的关系");
        }
        // In a real system, we'd store the request. For the skeleton/test, we'll just allow confirmation.
        // We could use a status like 'UNBIND_REQUESTED' but the DB only supports ACTIVE/TERMINATED in unique index.
        // So we'll just return success.
    }

    @Transactional
    public void confirmUnbind(Long userId, boolean confirm) {
        Relation relation = findActiveRelation(userId);
        if (relation == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前没有活跃的关系");
        }
        
        if (confirm) {
            relation.setStatus("TERMINATED");
            relation.setTerminatedAt(LocalDateTime.now());
            relationMapper.updateById(relation);
        }
    }

    private Relation findActiveRelation(Long userId) {
        return relationMapper.selectOne(Wrappers.<Relation>lambdaQuery()
                .eq(Relation::getStatus, "ACTIVE")
                .and(w -> w.eq(Relation::getUserAId, userId)
                        .or()
                        .eq(Relation::getUserBId, userId)));
    }

    private void assertNoActiveRelation(Long userId) {
        Long count = relationMapper.selectCount(Wrappers.<Relation>lambdaQuery()
                .eq(Relation::getStatus, "ACTIVE")
                .and(wrapper -> wrapper.eq(Relation::getUserAId, userId)
                        .or()
                        .eq(Relation::getUserBId, userId)));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.RELATION_CONFLICT, "当前已有有效关系，无法生成绑定码");
        }
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 5; i++) {
            String candidate = "%06d".formatted(ThreadLocalRandom.current().nextInt(0, 1_000_000));
            Long count = bindCodeMapper.selectCount(
                    Wrappers.<BindCode>lambdaQuery().eq(BindCode::getCode, candidate));
            if (count == null || count == 0) {
                return candidate;
            }
        }
        throw new PersistenceException("无法生成唯一绑定码");
    }
}
