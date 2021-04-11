package sec.hdlt.user.domain

import java.security.KeyStore
import java.security.PrivateKey

data class Database(val id: Int, val keyStore: KeyStore, val key: PrivateKey, val byzantineLevel: Int, val epochs: MutableMap<Int, EpochInfo>) {
    companion object {
        var id: Int = 0
        lateinit var keyStore: KeyStore
        lateinit var key: PrivateKey
        var byzantineLevel: Int = 0
        lateinit var epochs: MutableMap<Int, EpochInfo>
    }

    init {
        Database.id = id
        Database.keyStore = keyStore
        Database.key = key
        Database.byzantineLevel = byzantineLevel
        Database.epochs = epochs
    }
}