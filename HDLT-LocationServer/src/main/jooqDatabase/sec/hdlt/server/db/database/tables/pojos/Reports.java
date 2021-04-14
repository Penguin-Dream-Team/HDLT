/*
 * This file is generated by jOOQ.
 */
package sec.hdlt.server.db.database.tables.pojos;


import java.io.Serializable;

import org.jooq.types.UInteger;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Reports implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Integer  id;
    private final UInteger epoch;
    private final UInteger userId;
    private final UInteger x;
    private final UInteger y;

    public Reports(Reports value) {
        this.id = value.id;
        this.epoch = value.epoch;
        this.userId = value.userId;
        this.x = value.x;
        this.y = value.y;
    }

    public Reports(
        Integer  id,
        UInteger epoch,
        UInteger userId,
        UInteger x,
        UInteger y
    ) {
        this.id = id;
        this.epoch = epoch;
        this.userId = userId;
        this.x = x;
        this.y = y;
    }

    /**
     * Getter for <code>reports.id</code>.
     */
    public Integer getId() {
        return this.id;
    }

    /**
     * Getter for <code>reports.epoch</code>.
     */
    public UInteger getEpoch() {
        return this.epoch;
    }

    /**
     * Getter for <code>reports.user_id</code>.
     */
    public UInteger getUserId() {
        return this.userId;
    }

    /**
     * Getter for <code>reports.x</code>.
     */
    public UInteger getX() {
        return this.x;
    }

    /**
     * Getter for <code>reports.y</code>.
     */
    public UInteger getY() {
        return this.y;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Reports (");

        sb.append(id);
        sb.append(", ").append(epoch);
        sb.append(", ").append(userId);
        sb.append(", ").append(x);
        sb.append(", ").append(y);

        sb.append(")");
        return sb.toString();
    }
}