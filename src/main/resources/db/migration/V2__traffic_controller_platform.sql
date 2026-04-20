-- V2__traffic_controller_platform.sql: campaigns, contributions, intersections, congestion snapshots

CREATE TABLE campaigns (
    id              BIGSERIAL       PRIMARY KEY,
    title           VARCHAR(255)    NOT NULL,
    description     TEXT,
    target_amount   NUMERIC(12,2)   NOT NULL CHECK (target_amount > 0),
    current_amount  NUMERIC(12,2)   NOT NULL DEFAULT 0,
    status          VARCHAR(32)     NOT NULL,
    window_start    DATE            NOT NULL,
    window_end      DATE            NOT NULL,
    locked_at       TIMESTAMPTZ,
    created_by_admin_id BIGINT      NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE contributions (
    id              BIGSERIAL       PRIMARY KEY,
    campaign_id     BIGINT          NOT NULL REFERENCES campaigns(id),
    driver_id       BIGINT          NOT NULL REFERENCES users(id),
    amount          NUMERIC(12,2)   NOT NULL CHECK (amount > 0),
    period          VARCHAR(32)     NOT NULL,
    contributed_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_contributions_campaign_id ON contributions(campaign_id);

CREATE TABLE intersections (
    id              BIGSERIAL       PRIMARY KEY,
    label           VARCHAR(255)    NOT NULL,
    description     TEXT,
    latitude        DOUBLE PRECISION NOT NULL CHECK (latitude >= -90 AND latitude <= 90),
    longitude       DOUBLE PRECISION NOT NULL CHECK (longitude >= -180 AND longitude <= 180),
    type            VARCHAR(32)     NOT NULL,
    status          VARCHAR(32)     NOT NULL,
    congestion_score DOUBLE PRECISION CHECK (congestion_score >= 0.0 AND congestion_score <= 1.0),
    last_checked_at TIMESTAMPTZ
);

CREATE INDEX idx_intersections_status ON intersections(status);

CREATE TABLE congestion_snapshots (
    id              BIGSERIAL       PRIMARY KEY,
    intersection_id BIGINT          NOT NULL REFERENCES intersections(id),
    score           DOUBLE PRECISION NOT NULL CHECK (score >= 0.0 AND score <= 1.0),
    raw_level       VARCHAR(64),
    provider        VARCHAR(64)     NOT NULL,
    measured_at     TIMESTAMPTZ     NOT NULL,
    recorded_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_congestion_snapshots_intersection_recorded
    ON congestion_snapshots(intersection_id, recorded_at);
