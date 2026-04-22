-- V4__platform_enhancements_phase2.sql: commute profiles, notifications, recurrence patterns, campaign-intersection join table, user extensions

-- Commute Profiles
CREATE TABLE commute_profiles (
    id                    BIGSERIAL       PRIMARY KEY,
    driver_id             BIGINT          NOT NULL UNIQUE REFERENCES users(id),
    origin_latitude       DOUBLE PRECISION NOT NULL,
    origin_longitude      DOUBLE PRECISION NOT NULL,
    destination_latitude  DOUBLE PRECISION NOT NULL,
    destination_longitude DOUBLE PRECISION NOT NULL,
    departure_start_time  TIME            NOT NULL,
    departure_end_time    TIME            NOT NULL,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ
);

-- Notifications
CREATE TABLE notifications (
    id           BIGSERIAL       PRIMARY KEY,
    user_id      BIGINT          NOT NULL REFERENCES users(id),
    type         VARCHAR(30)     NOT NULL,
    title        VARCHAR(500)    NOT NULL,
    body         TEXT            NOT NULL,
    metadata     JSONB,
    action_url   VARCHAR(500),
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT now(),
    read_at      TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_user_type ON notifications(user_id, type);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, read_at);

-- Recurrence Patterns
CREATE TABLE recurrence_patterns (
    id                       BIGSERIAL       PRIMARY KEY,
    intersection_id          BIGINT          NOT NULL REFERENCES intersections(id),
    day_of_week              VARCHAR(10)     NOT NULL,
    time_bucket_start        TIME            NOT NULL,
    time_bucket_end          TIME            NOT NULL,
    occurrence_count         INTEGER         NOT NULL,
    average_congestion_score DOUBLE PRECISION NOT NULL,
    detected_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    pattern_data             JSONB
);

CREATE INDEX idx_recurrence_patterns_intersection ON recurrence_patterns(intersection_id);

-- Campaign-Intersection join table
CREATE TABLE campaign_intersections (
    id              BIGSERIAL       PRIMARY KEY,
    campaign_id     BIGINT          NOT NULL REFERENCES campaigns(id),
    intersection_id BIGINT          NOT NULL REFERENCES intersections(id),
    UNIQUE(campaign_id, intersection_id)
);

CREATE INDEX idx_campaign_intersections_campaign ON campaign_intersections(campaign_id);
CREATE INDEX idx_campaign_intersections_intersection ON campaign_intersections(intersection_id);

-- User extensions
ALTER TABLE users ADD COLUMN home_latitude DOUBLE PRECISION;
ALTER TABLE users ADD COLUMN home_longitude DOUBLE PRECISION;
ALTER TABLE users ADD COLUMN commute_notifications_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN job_notifications_enabled BOOLEAN NOT NULL DEFAULT true;
