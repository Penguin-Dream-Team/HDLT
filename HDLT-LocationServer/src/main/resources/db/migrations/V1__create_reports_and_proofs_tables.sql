/**
 * Create reports table
 */
CREATE TABLE reports
(
    id      SERIAL PRIMARY KEY,
    epoch   INT UNSIGNED NOT NULL,
    user_id INT UNSIGNED NOT NULL,
    x       INT UNSIGNED NOT NULL,
    y       INT UNSIGNED NOT NULL
);

/**
 * Create proofs table
 */
CREATE TABLE proofs
(
    id            SERIAL PRIMARY KEY,
    other_user_id INT UNSIGNED NOT NULL,
    report_id     INT UNSIGNED NOT NULL,
    signature     VARCHAR(64) NOT NULL,
    FOREIGN KEY (report_id) REFERENCES reports (id) ON UPDATE CASCADE ON DELETE CASCADE
);
