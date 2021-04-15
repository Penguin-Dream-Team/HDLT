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
public class Nonces implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UInteger userId;
    private final byte[]   nonce;

    public Nonces(Nonces value) {
        this.userId = value.userId;
        this.nonce = value.nonce;
    }

    public Nonces(
        UInteger userId,
        byte[]   nonce
    ) {
        this.userId = userId;
        this.nonce = nonce;
    }

    /**
     * Getter for <code>nonces.user_id</code>.
     */
    public UInteger getUserId() {
        return this.userId;
    }

    /**
     * Getter for <code>nonces.nonce</code>.
     */
    public byte[] getNonce() {
        return this.nonce;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Nonces (");

        sb.append(userId);
        sb.append(", ").append("[binary...]");

        sb.append(")");
        return sb.toString();
    }
}
