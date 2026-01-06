package com.mingyu.app.relationship.repository;

// Implements 1.账号与关系管理

import com.mingyu.app.relationship.model.DeviceEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<DeviceEntity, Long> {

    Optional<DeviceEntity> findByDeviceIdentifier(String deviceIdentifier);

    Optional<DeviceEntity> findByUserId(Long userId);
}