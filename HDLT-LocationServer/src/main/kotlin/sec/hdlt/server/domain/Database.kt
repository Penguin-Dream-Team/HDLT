package sec.hdlt.server.domain

import sec.hdlt.server.dao.NonceDAO
import sec.hdlt.server.dao.ReportDAO
import sec.hdlt.server.dao.RequestsDAO
import java.security.KeyStore
import java.security.PrivateKey

data class Database(
    val keyStore: KeyStore,
    val key: PrivateKey,
    val reportDAO: ReportDAO,
    val nonceDAO: NonceDAO,
    val requestsDAO: RequestsDAO
) {
    companion object {
        lateinit var keyStore: KeyStore
        lateinit var key: PrivateKey
        lateinit var reportDAO: ReportDAO
        lateinit var nonceDAO: NonceDAO
        lateinit var requestsDAO: RequestsDAO
    }

    init {
        Database.keyStore = keyStore
        Database.key = key
        Database.reportDAO = reportDAO
        Database.nonceDAO = nonceDAO
        Database.requestsDAO = requestsDAO
    }
}