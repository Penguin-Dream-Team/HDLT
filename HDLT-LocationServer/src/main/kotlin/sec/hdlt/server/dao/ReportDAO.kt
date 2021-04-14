package sec.hdlt.server.dao

import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.types.UInteger
import sec.hdlt.server.data.Coordinates
import sec.hdlt.server.data.ReportInfo
import sec.hdlt.server.db.database.Tables.REPORTS

class ReportDAO(
    conf: Configuration
) {
    private val dslContext = DSL.using(conf)

    fun hasUserReport(userId: Int, epoch: Int, create: DSLContext = dslContext): Boolean {
        return create.fetchCount(
            create.select(REPORTS.ID)
                .from(REPORTS)
                .where(REPORTS.USER_ID.eq(userId))
                .and(REPORTS.EPOCH.eq(epoch))
        ) != 0
    }

    fun getUserReport(userId: Int, epoch: Int, create: DSLContext = dslContext): ReportInfo {
        return create.select()
            .from(REPORTS)
            .where(REPORTS.USER_ID.eq(userId))
            .and(REPORTS.EPOCH.eq(epoch))
            .fetchOne()?.map {
                ReportInfo(
                    id = it[REPORTS.USER_ID].toInt(),
                    epoch = it[REPORTS.EPOCH].toInt(),
                    coordinates = Coordinates(
                        x = it[REPORTS.X].toInt(),
                        y = it[REPORTS.Y].toInt()
                    )
                )
            } ?: throw NotImplementedError("TODO: MAKE A NOT FOUND ERROR WE CAN CATCH OUTSIDE!")
    }

}

/**
 * Helper extension to avoid repetition
 */
private fun <R : Record> TableField<R, UInteger>.eq(other: Int): Condition {
    return eq(UInteger.valueOf(other))
}
