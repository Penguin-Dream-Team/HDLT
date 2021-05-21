package sec.hdlt.server.dao

import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import sec.hdlt.server.db.database.Tables.*

class NonceDAO(
    conf: Configuration
) : AbstractDAO() {
    private val dslContext = DSL.using(conf)

    /**
     * @throws DataAccessException if duplicate nonce for user
     */
    fun storeUserNonce(nonce: ByteArray, userId: Int, create: DSLContext = dslContext): Boolean {
        return create.insertInto(NONCES)
            .set(NONCES.NONCE, nonce)
            .set(NONCES.USER_ID, userId)
            .execute() == 1
    }

    /**
     * @throws DataAccessException if duplicate nonce for user
     */
    fun storeHANonce(nonce: ByteArray, create: DSLContext = dslContext): Boolean {
        return create.insertInto(HA_NONCES)
            .set(HA_NONCES.NONCE, nonce)
            .execute() == 1
    }

    /**
     * @throws DataAccessException if duplicate nonce for user
     */
    fun storeServerNonce(nonce: ByteArray, serverId: Int, create: DSLContext = dslContext): Boolean {
        return create.insertInto(BROADCAST_NONCES)
            .set(BROADCAST_NONCES.NONCE, nonce)
            .set(BROADCAST_NONCES.SERVER_ID, serverId)
            .execute() == 1
    }
}