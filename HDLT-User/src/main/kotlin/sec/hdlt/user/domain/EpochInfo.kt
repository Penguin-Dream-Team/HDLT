package sec.hdlt.user.domain

import java.security.KeyStore
import java.security.PrivateKey

data class EpochInfo(val id: Int, var position: Coordinates, var epoch: Int, var board: Board, val key: PrivateKey, val keyStore: KeyStore, val byzantineLevel: Int) {
    fun clone(): EpochInfo {
        return EpochInfo(id, Coordinates(position.x, position.y), epoch, board.clone(), key, keyStore, byzantineLevel)
    }
}