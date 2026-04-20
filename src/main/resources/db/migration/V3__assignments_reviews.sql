-- V3__assignments_reviews.sql: controller applications, assignments, shift slots, reviews

CREATE TABLE controller_applications (
    id              BIGSERIAL       PRIMARY KEY,
    campaign_id     BIGINT          NOT NULL REFERENCES campaigns(id),
    controller_id   BIGINT          NOT NULL REFERENCES users(id),
    status          VARCHAR(32)     NOT NULL,
    note            TEXT,
    applied_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_application_campaign_controller UNIQUE (campaign_id, controller_id)
);

CREATE TABLE assignments (
    id              BIGSERIAL       PRIMARY KEY,
    campaign_id     BIGINT          NOT NULL REFERENCES campaigns(id),
    controller_id   BIGINT          NOT NULL REFERENCES users(id),
    intersection_id BIGINT          NOT NULL REFERENCES intersections(id),
    status          VARCHAR(32)     NOT NULL,
    agreed_pay      NUMERIC(12,2),
    assigned_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    paid_at         TIMESTAMPTZ
);

CREATE INDEX idx_assignments_campaign_intersection ON assignments(campaign_id, intersection_id);

CREATE TABLE shift_slots (
    id              BIGSERIAL       PRIMARY KEY,
    assignment_id   BIGINT          NOT NULL REFERENCES assignments(id),
    intersection_id BIGINT          NOT NULL REFERENCES intersections(id),
    date            DATE            NOT NULL,
    shift_type      VARCHAR(32)     NOT NULL,
    start_time      TIME            NOT NULL,
    end_time        TIME            NOT NULL,
    completed       BOOLEAN         NOT NULL DEFAULT false,
    CONSTRAINT uq_shift_slot_intersection_date_type UNIQUE (intersection_id, date, shift_type)
);

CREATE INDEX idx_shift_slots_assignment_date_type ON shift_slots(assignment_id, date, shift_type);

CREATE TABLE reviews (
    id              BIGSERIAL       PRIMARY KEY,
    assignment_id   BIGINT          NOT NULL REFERENCES assignments(id),
    driver_id       BIGINT          NOT NULL REFERENCES users(id),
    rating          INTEGER         NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment         TEXT,
    reviewed_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_review_assignment_driver UNIQUE (assignment_id, driver_id)
);

CREATE INDEX idx_reviews_assignment_id ON reviews(assignment_id);
