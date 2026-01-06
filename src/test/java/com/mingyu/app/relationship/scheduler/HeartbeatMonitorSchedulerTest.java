package com.mingyu.app.relationship.scheduler;

// Implements 6.开发者核心逻辑

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mingyu.app.notification.model.NotificationDispatchRequest;
import com.mingyu.app.notification.model.NotificationEventType;
import com.mingyu.app.notification.service.NotificationDispatcherService;
import com.mingyu.app.relationship.model.DeviceEntity;
import com.mingyu.app.relationship.model.DeviceHeartbeatState;
import com.mingyu.app.relationship.model.RelationshipEntity;
import com.mingyu.app.relationship.model.RelationshipStatus;
import com.mingyu.app.relationship.repository.DeviceRepository;
import com.mingyu.app.relationship.repository.RelationshipRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeartbeatMonitorSchedulerTest {

    @Mock
    private RelationshipRepository relationshipRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private NotificationDispatcherService notificationDispatcherService;

    @Mock
    private Clock clock;

    private HeartbeatMonitorScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HeartbeatMonitorScheduler(
                relationshipRepository, deviceRepository, notificationDispatcherService, clock);
    }

    @Test
    void marksDeviceLostAndEscalates() {
        Instant now = Instant.parse("2024-01-01T10:00:00Z");
        when(clock.instant()).thenReturn(now);

        RelationshipEntity relationship = new RelationshipEntity();
        relationship.setStatus(RelationshipStatus.ACTIVE);
        relationship.setInitiatorUserId(1L);
        relationship.setPartnerUserId(2L);

        DeviceEntity monitoredDevice = new DeviceEntity();
        monitoredDevice.setUserId(2L);
        monitoredDevice.setDeviceIdentifier("device-b");
        monitoredDevice.setPushToken("push-token");
        monitoredDevice.setHeartbeatState(DeviceHeartbeatState.HEALTHY);
        monitoredDevice.setLastSeenAt(now.minus(6, ChronoUnit.MINUTES));

        when(relationshipRepository.findAllActiveRelationships()).thenReturn(List.of(relationship));
        when(deviceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(deviceRepository.findByUserId(2L)).thenReturn(Optional.of(monitoredDevice));

        scheduler.monitorHeartbeats();

        verify(deviceRepository).save(monitoredDevice);
        assertEquals(DeviceHeartbeatState.LOST, monitoredDevice.getHeartbeatState());

        ArgumentCaptor<NotificationDispatchRequest> requestCaptor =
                ArgumentCaptor.forClass(NotificationDispatchRequest.class);
        verify(notificationDispatcherService).dispatch(requestCaptor.capture(), isNull());

        NotificationDispatchRequest request = requestCaptor.getValue();
        assertEquals(NotificationEventType.HEARTBEAT_FAILURE, request.getEventType());
        assertEquals("device-b", request.getAttributes().get("deviceIdentifier"));
        assertEquals(1L, request.getRecipient().getMonitorUserId());
        assertEquals(2L, request.getRecipient().getMonitoredUserId());
    }

    @Test
    void skipsEscalationForFreshHeartbeat() {
        Instant now = Instant.parse("2024-01-01T10:00:00Z");
        when(clock.instant()).thenReturn(now);

        RelationshipEntity relationship = new RelationshipEntity();
        relationship.setStatus(RelationshipStatus.ACTIVE);
        relationship.setInitiatorUserId(1L);
        relationship.setPartnerUserId(2L);

        DeviceEntity monitoredDevice = new DeviceEntity();
        monitoredDevice.setUserId(2L);
        monitoredDevice.setDeviceIdentifier("device-b");
        monitoredDevice.setHeartbeatState(DeviceHeartbeatState.HEALTHY);
        monitoredDevice.setLastSeenAt(now.minus(2, ChronoUnit.MINUTES));

        when(relationshipRepository.findAllActiveRelationships()).thenReturn(List.of(relationship));
        when(deviceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(deviceRepository.findByUserId(2L)).thenReturn(Optional.of(monitoredDevice));

        scheduler.monitorHeartbeats();

        verify(deviceRepository, never()).save(any());
        verify(notificationDispatcherService, never()).dispatch(any(), any());
    }
}