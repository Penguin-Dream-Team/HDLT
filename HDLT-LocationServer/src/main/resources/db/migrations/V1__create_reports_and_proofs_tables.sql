/**
 * Create reports table
 */
CREATE TABLE reports
(
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    epoch   INTEGER UNSIGNED NOT NULL,
    user_id INTEGER UNSIGNED NOT NULL,
    x       INTEGER UNSIGNED NOT NULL,
    y       INTEGER UNSIGNED NOT NULL
);

/**
 * Create proofs table
 */
CREATE TABLE proofs
(
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    other_user_id INTEGER UNSIGNED NOT NULL,
    report_id     INTEGER UNSIGNED NOT NULL,
    signature     VARCHAR(64) NOT NULL,
    FOREIGN KEY (report_id) REFERENCES reports (id) ON UPDATE CASCADE ON DELETE CASCADE
);
