package sec.hdlt.user.domain

import sec.hdlt.user.CERT_SERVER_PREFIX
import sec.hdlt.user.ServerFrontend
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import kotlin.random.Random

/**
 *  Class to hold global data about the system
 */
object Database {
    var id: Int = 0
    lateinit var keyStore: KeyStore
    lateinit var key: PrivateKey
    var byzantineLevel: Int = 0
    val epochs: MutableMap<Int, EpochInfo> = mutableMapOf()
    lateinit var random: Random
    lateinit var frontend: ServerFrontend

    /**
     * Initialize Random with the given seed
     *
     * @param seed the seed to initialize Random
     */
    fun initRandom(seed: Long) {
        random = Random(seed)
    }

    /**
     * Initialize the server frontend
     *
     * @param host the servers' host
     * @param port the servers' base port
     * @param num the number of servers in the system
     */
    fun initServer(host: String, port: Int, num: Int) {
        frontend = ServerFrontend(host, port, num)
    }

    /**
     * Get certificate for server with given id
     *
     * @param id the id of the server
     * @return the certificate for the given server
     */
    fun getServerKey(id: Int): Certificate {
        return keyStore.getCertificate(CERT_SERVER_PREFIX + id)
    }
}