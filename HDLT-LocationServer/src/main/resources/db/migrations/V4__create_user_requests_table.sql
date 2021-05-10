/**
 * Create user requests table
 */
CREATE TABLE user_requests
(
    user_id INTEGER UNSIGNED NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, timestamp)
);