package com.mingyu.app.relationship.repository;

// Implements 1.账号与关系管理

import com.mingyu.app.relationship.model.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByPhoneNumber(String phoneNumber);
}