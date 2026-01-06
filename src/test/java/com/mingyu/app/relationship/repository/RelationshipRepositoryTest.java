package com.mingyu.app.relationship.repository;

// Implements 1.账号与关系管理

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mingyu.app.relationship.model.RelationshipEntity;
import com.mingyu.app.relationship.model.RelationshipStatus;
import com.mingyu.app.relationship.model.UserEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class RelationshipRepositoryTest {

    @Autowired
    private RelationshipRepository relationshipRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsLifecycleAndActiveLookup() {
        Instant baseTime = Instant.parse("2024-01-01T00:00:00Z");
        UserEntity initiator = persistUser("+8613711111111", "Watcher");
        UserEntity partner = persistUser("+8613711111112", "Subject");

        RelationshipEntity pending = new RelationshipEntity();
        pending.setInitiator(initiator);
        pending.setPartner(partner);
        pending.setBindingCode("456123");
        pending.setRequestedDeviceIdentifier("device-init");
        pending.setRequestedAt(baseTime);

        RelationshipEntity saved = relationshipRepository.save(pending);
        assertTrue(relationshipRepository.findActiveRelationshipForUser(initiator.getId()).isEmpty());

        saved.markActive("device-partner", baseTime.plusSeconds(60));
        relationshipRepository.save(saved);

        RelationshipEntity active = relationshipRepository
                .findActiveRelationshipForUser(partner.getId())
                .orElseThrow();
        assertEquals(RelationshipStatus.ACTIVE, active.getStatus());
        assertEquals("device-partner", active.getPartnerDeviceIdentifier());
        assertTrue(active.involvesUser(initiator.getId()));
        assertTrue(active.involvesUser(partner.getId()));

        saved.terminate("initiator override", baseTime.plusSeconds(120));
        relationshipRepository.save(saved);
        assertTrue(relationshipRepository.findActiveRelationshipForUser(partner.getId()).isEmpty());
    }

    private UserEntity persistUser(String phone, String name) {
        UserEntity user = new UserEntity();
        user.setPhoneNumber(phone);
        user.setDisplayName(name);
        user.setCountryCode("+86");
        return userRepository.save(user);
    }
}