package sec.hdlt.server.domain

import sec.hdlt.server.dao.NonceDAO
import sec.hdlt.server.dao.ReportDAO
import sec.hdlt.server.dao.RequestsDAO
import java.security.KeyStore
import java.security.PrivateKey

object Database{
        var id: Int = -1
        var numServers: Int = -1
        var quorum: Int = -1
        lateinit var keyStore: KeyStore
        lateinit var key: PrivateKey
        lateinit var reportDAO: ReportDAO
        lateinit var nonceDAO: NonceDAO
        lateinit var requestsDAO: RequestsDAO
}