package sec.hdlt.server.dao

import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.impl.DSL
import sec.hdlt.server.db.database.Tables.REPORTS

class ReportDAO(
    conf: Configuration
) {
    private val dslContext = DSL.using(conf)

    fun hello(create: DSLContext = dslContext) {
        val count = create.fetchCount(REPORTS)
        println("ok: $count")
    }

}