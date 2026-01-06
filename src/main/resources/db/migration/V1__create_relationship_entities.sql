-- Implements 1.账号与关系管理

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone_number VARCHAR(20) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    country_code VARCHAR(6),
    binding_code CHAR(6),
    binding_code_expires_at TIMESTAMP(6) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_users_phone UNIQUE (phone_number),
    CONSTRAINT uk_users_binding_code UNIQUE (binding_code)
);

CREATE TABLE IF NOT EXISTS devices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    device_identifier VARCHAR(64) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    model VARCHAR(64),
    os_version VARCHAR(32),
    app_version VARCHAR(32),
    push_token VARCHAR(128),
    binding_reason VARCHAR(128),
    active TINYINT(1) NOT NULL DEFAULT 1,
    bound_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_seen_at TIMESTAMP(6),
    revoked_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_devices_user UNIQUE (user_id),
    CONSTRAINT uk_devices_identifier UNIQUE (device_identifier),
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS relationships (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    initiator_user_id BIGINT NOT NULL,
    partner_user_id BIGINT NOT NULL,
    binding_code CHAR(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_device_identifier VARCHAR(64) NOT NULL,
    partner_device_identifier VARCHAR(64),
    requested_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    confirmed_at TIMESTAMP(6),
    terminated_at TIMESTAMP(6),
    termination_reason VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active_initiator_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN initiator_user_id END
    ) STORED,
    active_partner_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN partner_user_id END
    ) STORED,
    KEY idx_relationship_initiator (initiator_user_id),
    KEY idx_relationship_partner (partner_user_id),
    CONSTRAINT uk_relationship_binding_code UNIQUE (binding_code),
    CONSTRAINT uq_relationship_active_initiator UNIQUE (active_initiator_guard),
    CONSTRAINT uq_relationship_active_partner UNIQUE (active_partner_guard),
    CONSTRAINT fk_relationship_initiator FOREIGN KEY (initiator_user_id) REFERENCES users (id),
    CONSTRAINT fk_relationship_partner FOREIGN KEY (partner_user_id) REFERENCES users (id),
    CONSTRAINT chk_relationship_distinct_users CHECK (initiator_user_id <> partner_user_id)
);