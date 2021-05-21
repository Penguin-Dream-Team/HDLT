package sec.hdlt.server.domain

import kotlinx.serialization.Serializable

@Serializable
data class LocationReport(
    val id: Int,
    val epoch: Int,
    val location: Coordinates,
    val signature: String,
    val proofs: List<Proof>
) {
    override fun toString(): String {
        return "ID: $id EPOCH: $epoch COORDS: $location PROOFS: ${proofs.joinToString { " ${it.prover} " }}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocationReport

        if (id != other.id) return false
        if (epoch != other.epoch) return false
        if (location != other.location) return false
        if (signature != other.signature) return false
        if (proofs != other.proofs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + epoch
        result = 31 * result + location.hashCode()
        return result
    }
}