/*
 * This file is generated by jOOQ.
 */
package sec.hdlt.server.db.database.tables.pojos;


import java.io.Serializable;
import java.time.LocalDateTime;

import org.jooq.types.UInteger;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class UserRequests implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UInteger      userId;
    private final LocalDateTime timestamp;

    public UserRequests(UserRequests value) {
        this.userId = value.userId;
        this.timestamp = value.timestamp;
    }

    public UserRequests(
        UInteger      userId,
        LocalDateTime timestamp
    ) {
        this.userId = userId;
        this.timestamp = timestamp;
    }

    /**
     * Getter for <code>user_requests.user_id</code>.
     */
    public UInteger getUserId() {
        return this.userId;
    }

    /**
     * Getter for <code>user_requests.timestamp</code>.
     */
    public LocalDateTime getTimestamp() {
        return this.timestamp;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("UserRequests (");

        sb.append(userId);
        sb.append(", ").append(timestamp);

        sb.append(")");
        return sb.toString();
    }
}
