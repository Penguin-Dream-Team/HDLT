package sec.hdlt.server.domain

import sec.hdlt.server.dao.NonceDAO
import sec.hdlt.server.dao.ReportDAO
import java.security.KeyStore
import java.security.PrivateKey

data class Database(
    val keyStore: KeyStore,
    val key: PrivateKey,
    val reportDAO: ReportDAO,
    val nonceDAO: NonceDAO
) {
    companion object {
        lateinit var keyStore: KeyStore
        lateinit var key: PrivateKey
        lateinit var reportDAO: ReportDAO
        lateinit var nonceDAO: NonceDAO
    }

    init {
        Database.keyStore = keyStore
        Database.key = key
        Database.reportDAO = reportDAO
        Database.nonceDAO = nonceDAO
    }
}