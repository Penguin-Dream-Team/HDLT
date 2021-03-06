/*
 * This file is generated by jOOQ.
 */
package sec.hdlt.server.db.database.tables.records;


import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record4;
import org.jooq.Row4;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.UInteger;

import sec.hdlt.server.db.database.tables.Proofs;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ProofsRecord extends UpdatableRecordImpl<ProofsRecord> implements Record4<Integer, UInteger, UInteger, String> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>proofs.id</code>.
     */
    public ProofsRecord setId(Integer value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>proofs.id</code>.
     */
    public Integer getId() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>proofs.other_user_id</code>.
     */
    public ProofsRecord setOtherUserId(UInteger value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>proofs.other_user_id</code>.
     */
    public UInteger getOtherUserId() {
        return (UInteger) get(1);
    }

    /**
     * Setter for <code>proofs.report_id</code>.
     */
    public ProofsRecord setReportId(UInteger value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>proofs.report_id</code>.
     */
    public UInteger getReportId() {
        return (UInteger) get(2);
    }

    /**
     * Setter for <code>proofs.signature</code>.
     */
    public ProofsRecord setSignature(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>proofs.signature</code>.
     */
    public String getSignature() {
        return (String) get(3);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Integer> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record4 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row4<Integer, UInteger, UInteger, String> fieldsRow() {
        return (Row4) super.fieldsRow();
    }

    @Override
    public Row4<Integer, UInteger, UInteger, String> valuesRow() {
        return (Row4) super.valuesRow();
    }

    @Override
    public Field<Integer> field1() {
        return Proofs.PROOFS.ID;
    }

    @Override
    public Field<UInteger> field2() {
        return Proofs.PROOFS.OTHER_USER_ID;
    }

    @Override
    public Field<UInteger> field3() {
        return Proofs.PROOFS.REPORT_ID;
    }

    @Override
    public Field<String> field4() {
        return Proofs.PROOFS.SIGNATURE;
    }

    @Override
    public Integer component1() {
        return getId();
    }

    @Override
    public UInteger component2() {
        return getOtherUserId();
    }

    @Override
    public UInteger component3() {
        return getReportId();
    }

    @Override
    public String component4() {
        return getSignature();
    }

    @Override
    public Integer value1() {
        return getId();
    }

    @Override
    public UInteger value2() {
        return getOtherUserId();
    }

    @Override
    public UInteger value3() {
        return getReportId();
    }

    @Override
    public String value4() {
        return getSignature();
    }

    @Override
    public ProofsRecord value1(Integer value) {
        setId(value);
        return this;
    }

    @Override
    public ProofsRecord value2(UInteger value) {
        setOtherUserId(value);
        return this;
    }

    @Override
    public ProofsRecord value3(UInteger value) {
        setReportId(value);
        return this;
    }

    @Override
    public ProofsRecord value4(String value) {
        setSignature(value);
        return this;
    }

    @Override
    public ProofsRecord values(Integer value1, UInteger value2, UInteger value3, String value4) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached ProofsRecord
     */
    public ProofsRecord() {
        super(Proofs.PROOFS);
    }

    /**
     * Create a detached, initialised ProofsRecord
     */
    public ProofsRecord(Integer id, UInteger otherUserId, UInteger reportId, String signature) {
        super(Proofs.PROOFS);

        setId(id);
        setOtherUserId(otherUserId);
        setReportId(reportId);
        setSignature(signature);
    }
}
