package sec.hdlt.server.domain

import sec.hdlt.server.dao.NonceDAO
import sec.hdlt.server.dao.ReportDAO
import sec.hdlt.server.dao.RequestsDAO
import java.security.KeyStore
import java.security.PrivateKey
import java.util.*

object Database {
    var id: Int = -1
    var numServers: Int = -1
    var quorum: Int = -1
    lateinit var keyStore: KeyStore
    lateinit var key: PrivateKey
    lateinit var reportDAO: ReportDAO
    lateinit var nonceDAO: NonceDAO
    lateinit var requestsDAO: RequestsDAO

    lateinit var random: Random

    /**
     * Initialize Random with the given seed
     *
     * @param seed the seed to initialize Random
     */
    fun initRandom(seed: Long) {
        random = Random(seed)
    }
}