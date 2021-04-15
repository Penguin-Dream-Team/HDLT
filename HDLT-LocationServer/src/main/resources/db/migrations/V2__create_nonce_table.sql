/**
 * Create nonces table
 */
CREATE TABLE nonces
(
    user_id INTEGER UNSIGNED NOT NULL,
    nonce   VARBINARY(12) NOT NULL,
    PRIMARY KEY (user_id, nonce)
);