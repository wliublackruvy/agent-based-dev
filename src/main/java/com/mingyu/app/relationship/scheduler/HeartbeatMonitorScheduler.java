package com.mingyu.app.relationship.scheduler;

// Implements 6.开发者核心逻辑

import com.mingyu.app.notification.model.NotificationDispatchRequest;
import com.mingyu.app.notification.model.NotificationEventType;
import com.mingyu.app.notification.model.NotificationRecipient;
import com.mingyu.app.notification.service.NotificationDispatcherService;
import com.mingyu.app.relationship.model.DeviceEntity;
import com.mingyu.app.relationship.model.DeviceHeartbeatState;
import com.mingyu.app.relationship.model.RelationshipEntity;
import com.mingyu.app.relationship.repository.DeviceRepository;
import com.mingyu.app.relationship.repository.RelationshipRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HeartbeatMonitorScheduler {

    private static final Duration HEARTBEAT_STALE_THRESHOLD = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(HeartbeatMonitorScheduler.class);

    private final RelationshipRepository relationshipRepository;
    private final DeviceRepository deviceRepository;
    private final NotificationDispatcherService notificationDispatcherService;
    private final Clock clock;

    public HeartbeatMonitorScheduler(
            RelationshipRepository relationshipRepository,
            DeviceRepository deviceRepository,
            NotificationDispatcherService notificationDispatcherService,
            Clock clock) {
        this.relationshipRepository =
                Objects.requireNonNull(relationshipRepository, "relationshipRepository is required");
        this.deviceRepository = Objects.requireNonNull(deviceRepository, "deviceRepository is required");
        this.notificationDispatcherService =
                Objects.requireNonNull(notificationDispatcherService, "notificationDispatcherService is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void monitorHeartbeats() {
        Instant now = clock.instant();
        List<RelationshipEntity> relationships = relationshipRepository.findAllActiveRelationships();
        if (relationships.isEmpty()) {
            return;
        }
        for (RelationshipEntity relationship : relationships) {
            evaluateDevice(relationship.getInitiatorUserId(), relationship.getPartnerUserId(), now);
            evaluateDevice(relationship.getPartnerUserId(), relationship.getInitiatorUserId(), now);
        }
    }

    private void evaluateDevice(Long monitoredUserId, Long monitorUserId, Instant now) {
        if (monitoredUserId == null || monitorUserId == null) {
            return;
        }
        deviceRepository
                .findByUserId(monitoredUserId)
                .ifPresent(device -> handleDevice(device, monitorUserId, now));
    }

    private void handleDevice(DeviceEntity device, Long monitorUserId, Instant now) {
        if (isHeartbeatStale(device.getLastSeenAt(), now)) {
            markLostAndEscalate(device, monitorUserId, now);
        } else if (device.getHeartbeatState() == DeviceHeartbeatState.LOST) {
            device.setHeartbeatState(DeviceHeartbeatState.HEALTHY);
            deviceRepository.save(device);
            log.info("Device {} heartbeat recovered", device.getDeviceIdentifier());
        }
    }

    private boolean isHeartbeatStale(Instant lastSeenAt, Instant now) {
        if (lastSeenAt == null) {
            return true;
        }
        Duration gap = Duration.between(lastSeenAt, now);
        if (gap.isNegative()) {
            return false;
        }
        return gap.compareTo(HEARTBEAT_STALE_THRESHOLD) > 0;
    }

    private void markLostAndEscalate(DeviceEntity device, Long monitorUserId, Instant now) {
        if (device.getHeartbeatState() == DeviceHeartbeatState.LOST) {
            return;
        }
        device.setHeartbeatState(DeviceHeartbeatState.LOST);
        deviceRepository.save(device);
        notificationDispatcherService.dispatch(buildRequest(device, monitorUserId, now), null);
        log.warn(
                "Escalated heartbeat loss for device {} to monitor {}",
                device.getDeviceIdentifier(),
                monitorUserId);
    }

    private NotificationDispatchRequest buildRequest(DeviceEntity device, Long monitorUserId, Instant now) {
        NotificationRecipient recipient = NotificationRecipient.builder()
                .monitorUserId(monitorUserId)
                .monitoredUserId(device.getUserId())
                .deviceToken(device.getPushToken())
                .build();

        return NotificationDispatchRequest.builder()
                .eventType(NotificationEventType.HEARTBEAT_FAILURE)
                .recipient(recipient)
                .triggeredAt(now)
                .message("Heartbeat lost for device " + device.getDeviceIdentifier())
                .attribute("deviceIdentifier", device.getDeviceIdentifier())
                .attribute("monitorUserId", monitorUserId)
                .attribute("lastSeenAt", device.getLastSeenAt() != null ? device.getLastSeenAt().toString() : "unknown")
                .build();
    }
}