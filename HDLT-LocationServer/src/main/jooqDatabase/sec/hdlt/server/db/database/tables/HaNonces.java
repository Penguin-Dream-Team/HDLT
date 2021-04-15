/*
 * This file is generated by jOOQ.
 */
package sec.hdlt.server.db.database.tables;


import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row1;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

import sec.hdlt.server.db.database.DefaultSchema;
import sec.hdlt.server.db.database.Keys;
import sec.hdlt.server.db.database.tables.records.HaNoncesRecord;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class HaNonces extends TableImpl<HaNoncesRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>ha_nonces</code>
     */
    public static final HaNonces HA_NONCES = new HaNonces();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<HaNoncesRecord> getRecordType() {
        return HaNoncesRecord.class;
    }

    /**
     * The column <code>ha_nonces.nonce</code>.
     */
    public final TableField<HaNoncesRecord, byte[]> NONCE = createField(DSL.name("nonce"), SQLDataType.VARBINARY, this, "");

    private HaNonces(Name alias, Table<HaNoncesRecord> aliased) {
        this(alias, aliased, null);
    }

    private HaNonces(Name alias, Table<HaNoncesRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>ha_nonces</code> table reference
     */
    public HaNonces(String alias) {
        this(DSL.name(alias), HA_NONCES);
    }

    /**
     * Create an aliased <code>ha_nonces</code> table reference
     */
    public HaNonces(Name alias) {
        this(alias, HA_NONCES);
    }

    /**
     * Create a <code>ha_nonces</code> table reference
     */
    public HaNonces() {
        this(DSL.name("ha_nonces"), null);
    }

    public <O extends Record> HaNonces(Table<O> child, ForeignKey<O, HaNoncesRecord> key) {
        super(child, key, HA_NONCES);
    }

    @Override
    public Schema getSchema() {
        return DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public UniqueKey<HaNoncesRecord> getPrimaryKey() {
        return Keys.PK_HA_NONCES;
    }

    @Override
    public List<UniqueKey<HaNoncesRecord>> getKeys() {
        return Arrays.<UniqueKey<HaNoncesRecord>>asList(Keys.PK_HA_NONCES);
    }

    @Override
    public HaNonces as(String alias) {
        return new HaNonces(DSL.name(alias), this);
    }

    @Override
    public HaNonces as(Name alias) {
        return new HaNonces(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public HaNonces rename(String name) {
        return new HaNonces(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public HaNonces rename(Name name) {
        return new HaNonces(name, null);
    }

    // -------------------------------------------------------------------------
    // Row1 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row1<byte[]> fieldsRow() {
        return (Row1) super.fieldsRow();
    }
}
