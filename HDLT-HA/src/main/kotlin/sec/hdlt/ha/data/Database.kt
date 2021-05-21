package sec.hdlt.ha.data

import sec.hdlt.ha.CERT_SERVER_PREFIX
import sec.hdlt.ha.CommunicationService
import sec.hdlt.ha.ServerFrontend
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.*

/**
 *  Class to hold global data about the system
 */
object Database {
    lateinit var keyStore: KeyStore
    lateinit var key: PrivateKey
    lateinit var frontend: ServerFrontend

    /**
     * Initialize the server frontend
     *
     * @param host the servers' host
     * @param port the servers' base port
     * @param num the number of servers in the system
     * @param byzantine the number of byzantine servers in the system
     */
    fun initServer(host: String, port: Int, num: Int, byzantine: Int) {
        frontend = ServerFrontend(host, port, num, (num + byzantine)/2)
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