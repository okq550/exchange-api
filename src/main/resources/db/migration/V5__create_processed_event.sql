CREATE TABLE processed_event
(
    event_id     VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);