/**
 * Create nonces table
 */
CREATE TABLE broadcast_nonces
(
    server_id INTEGER UNSIGNED NOT NULL,
    nonce   VARBINARY(12) NOT NULL,
    PRIMARY KEY (server_id, nonce)
);