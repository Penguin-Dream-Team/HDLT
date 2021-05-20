package sec.hdlt.ha.data

import kotlinx.serialization.Serializable

@Serializable
data class Report(
    val id: Int,
    val epoch: Int,
    val location: Coordinates,
    val signature: String,
    val proofs: List<Proof>
) {
    override fun toString(): String {
        return "ID: $id EPOCH: $epoch COORDS: $location PROOFS: $proofs"
    }
}