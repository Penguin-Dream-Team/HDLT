package sec.hdlt.user.dto

import kotlinx.serialization.Serializable
import sec.hdlt.user.domain.Coordinates

/**
 * Object for server response to request of location info at given epoch
 */
@Serializable
data class LocationResponse (val id: Int, val epoch: Int, val coords: Coordinates, val serverInfo: String, val signature: String, val proofs: List<ProofDto>) {
    override fun toString(): String {
        return "ID: $id, Epoch: $epoch, Coordinates: $coords, Status: $serverInfo, Proofs: $proofs"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocationResponse

        if (id != other.id) return false
        if (epoch != other.epoch) return false
        if (coords != other.coords) return false
        if (serverInfo != other.serverInfo) return false
        if (proofs != other.proofs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + epoch
        result = 31 * result + coords.hashCode()
        return result
    }
}