/*
 * This file is generated by jOOQ.
 */
package sec.hdlt.server.db.database;


import sec.hdlt.server.db.database.tables.BroadcastNonces;
import sec.hdlt.server.db.database.tables.HaNonces;
import sec.hdlt.server.db.database.tables.Nonces;
import sec.hdlt.server.db.database.tables.Proofs;
import sec.hdlt.server.db.database.tables.Reports;
import sec.hdlt.server.db.database.tables.SqliteSequence;
import sec.hdlt.server.db.database.tables.UserRequests;


/**
 * Convenience access to all tables in the default schema.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Tables {

    /**
     * The table <code>broadcast_nonces</code>.
     */
    public static final BroadcastNonces BROADCAST_NONCES = BroadcastNonces.BROADCAST_NONCES;

    /**
     * The table <code>ha_nonces</code>.
     */
    public static final HaNonces HA_NONCES = HaNonces.HA_NONCES;

    /**
     * The table <code>nonces</code>.
     */
    public static final Nonces NONCES = Nonces.NONCES;

    /**
     * The table <code>proofs</code>.
     */
    public static final Proofs PROOFS = Proofs.PROOFS;

    /**
     * The table <code>reports</code>.
     */
    public static final Reports REPORTS = Reports.REPORTS;

    /**
     * The table <code>sqlite_sequence</code>.
     */
    public static final SqliteSequence SQLITE_SEQUENCE = SqliteSequence.SQLITE_SEQUENCE;

    /**
     * The table <code>user_requests</code>.
     */
    public static final UserRequests USER_REQUESTS = UserRequests.USER_REQUESTS;
}
