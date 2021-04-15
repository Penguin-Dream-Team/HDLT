package sec.hdlt.server.dao

import org.jooq.Condition
import org.jooq.InsertSetMoreStep
import org.jooq.InsertSetStep
import org.jooq.TableField
import org.jooq.Record
import org.jooq.types.UInteger

abstract class AbstractDAO {

    fun <R : Record> InsertSetMoreStep<R>.set(column: TableField<R, UInteger>, value: Int): InsertSetMoreStep<R> {
        return set(column, UInteger.valueOf(value))
    }

    fun <R : Record> InsertSetStep<R>.set(column: TableField<R, UInteger>, value: Int): InsertSetMoreStep<R> {
        return set(column, UInteger.valueOf(value))
    }

    /**
     * Helper extension to avoid repetition
     */
    fun <R : Record> TableField<R, UInteger>.eq(other: Int): Condition {
        return eq(UInteger.valueOf(other))
    }
}