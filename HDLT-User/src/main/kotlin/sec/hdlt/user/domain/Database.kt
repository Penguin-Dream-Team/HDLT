package sec.hdlt.user.domain

import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import javax.xml.crypto.Data
import kotlin.random.Random

data class Database(
    val id: Int,
    val keyStore: KeyStore,
    val key: PrivateKey,
    val serverCert: Certificate,
    val byzantineLevel: Int,
    val epochs: MutableMap<Int, EpochInfo>,
    val random: Random,
    val serverHost: String,
    val serverPortBase: Int,
    val numServers: Int
) {
    companion object {
        var id: Int = 0
        lateinit var keyStore: KeyStore
        lateinit var key: PrivateKey
        lateinit var serverCert: Certificate
        var byzantineLevel: Int = 0
        lateinit var epochs: MutableMap<Int, EpochInfo>
        lateinit var random: Random
        lateinit var serverHost: String
        var serverPortBase: Int = 0
        var numServers: Int = 0
    }

    init {
        Database.id = id
        Database.keyStore = keyStore
        Database.key = key
        Database.serverCert = serverCert
        Database.byzantineLevel = byzantineLevel
        Database.epochs = epochs
        Database.random = random
        Database.serverHost = serverHost
        Database.serverPortBase = serverPortBase
        Database.numServers = numServers
    }
}