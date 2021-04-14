package sec.hdlt.server.data

import sec.hdlt.server.dao.ReportDAO
import java.security.KeyStore
import java.security.PrivateKey

data class Database(val keyStore: KeyStore, val key: PrivateKey, val reportDAO: ReportDAO) {
    companion object {
        lateinit var keyStore: KeyStore
        lateinit var key: PrivateKey
        lateinit var reportDAO: ReportDAO
    }

    init {
        Database.keyStore = keyStore
        Database.key = key
        Database.reportDAO = reportDAO
    }
}