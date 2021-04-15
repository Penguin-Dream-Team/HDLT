/*
 * This file is generated by jOOQ.
 */
package sec.hdlt.server.db.database.tables.records;


import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Row1;
import org.jooq.impl.UpdatableRecordImpl;

import sec.hdlt.server.db.database.tables.HaNonces;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class HaNoncesRecord extends UpdatableRecordImpl<HaNoncesRecord> implements Record1<byte[]> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>ha_nonces.nonce</code>.
     */
    public HaNoncesRecord setNonce(byte[] value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>ha_nonces.nonce</code>.
     */
    public byte[] getNonce() {
        return (byte[]) get(0);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<byte[]> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record1 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row1<byte[]> fieldsRow() {
        return (Row1) super.fieldsRow();
    }

    @Override
    public Row1<byte[]> valuesRow() {
        return (Row1) super.valuesRow();
    }

    @Override
    public Field<byte[]> field1() {
        return HaNonces.HA_NONCES.NONCE;
    }

    @Override
    public byte[] component1() {
        return getNonce();
    }

    @Override
    public byte[] value1() {
        return getNonce();
    }

    @Override
    public HaNoncesRecord value1(byte[] value) {
        setNonce(value);
        return this;
    }

    @Override
    public HaNoncesRecord values(byte[] value1) {
        value1(value1);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached HaNoncesRecord
     */
    public HaNoncesRecord() {
        super(HaNonces.HA_NONCES);
    }

    /**
     * Create a detached, initialised HaNoncesRecord
     */
    public HaNoncesRecord(byte[] nonce) {
        super(HaNonces.HA_NONCES);

        setNonce(nonce);
    }
}
