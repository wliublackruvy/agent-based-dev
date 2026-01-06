-- Implements 4.会员审计功能

CREATE TABLE IF NOT EXISTS audit_log_entries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_user_id BIGINT,
    relationship_id BIGINT,
    event_type VARCHAR(32) NOT NULL,
    event_time TIMESTAMP(6) NOT NULL,
    event_date DATE NOT NULL,
    device_identifier VARCHAR(64),
    application_id VARCHAR(128),
    platform VARCHAR(32),
    duration_seconds BIGINT,
    count_value INT,
    metadata_json TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_audit_user_time (user_id, event_time),
    KEY idx_audit_type_time (event_type, event_time),
    KEY idx_audit_relationship_time (relationship_id, event_time),
    CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_audit_log_target FOREIGN KEY (target_user_id) REFERENCES users (id),
    CONSTRAINT fk_audit_log_relationship FOREIGN KEY (relationship_id) REFERENCES relationships (id)
);