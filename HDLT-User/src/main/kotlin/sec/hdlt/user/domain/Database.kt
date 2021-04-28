package sec.hdlt.user.domain

import sec.hdlt.user.ServerFrontend
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import kotlin.random.Random

object Database {
    var id: Int = 0
    lateinit var keyStore: KeyStore
    lateinit var key: PrivateKey
    lateinit var serverCert: Certificate
    var byzantineLevel: Int = 0
    val epochs: MutableMap<Int, EpochInfo> = mutableMapOf()
    lateinit var random: Random
    lateinit var frontend: ServerFrontend

    fun initRandom(seed: Long) {
        random = Random(seed)
    }

    fun initServer(host: String, port: Int, num: Int) {
        frontend = ServerFrontend(host, port, num)
    }
}