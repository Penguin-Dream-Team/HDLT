package sec.hdlt.server.antispam

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jooq.SQLDialect
import org.jooq.impl.DefaultConfiguration
import sec.hdlt.server.dao.ReportDAO

fun main() {
    val dbConfig = DefaultConfiguration()
        .set(SQLDialect.SQLITE)
        .set(HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:HDLT-LocationServer/src/main/resources/db/database7777.sqlite"
            maximumPoolSize = 15
        }))
    val reportDao = ReportDAO(dbConfig)

    reportDao.getWitnessProofs(0, listOf(1, 2, 3, 4, 5)).forEach { proof ->
        println(proof)
    }
}
