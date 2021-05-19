package sec.hdlt.server.dao

import org.jooq.*
import org.jooq.impl.DSL
import sec.hdlt.server.db.database.Tables.*
import java.time.LocalDateTime

class RequestsDAO(
    conf: Configuration
) : AbstractDAO() {
    private val dslContext = DSL.using(conf)

    fun saveUserRequest(userId: Int, create: DSLContext = dslContext) {
        val timeStamp = LocalDateTime.now()
        create.transaction { configuration ->
            val transaction = DSL.using(configuration)
            transaction.insertInto(USER_REQUESTS)
                .set(USER_REQUESTS.USER_ID, userId)
                .set(USER_REQUESTS.TIMESTAMP, timeStamp)
                .execute()
        }
    }
}

