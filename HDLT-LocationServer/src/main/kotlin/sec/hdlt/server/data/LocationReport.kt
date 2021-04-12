package sec.hdlt.server.data

import kotlinx.serialization.Serializable

@Serializable
data class LocationReport(
    val id: Int,
    val epoch: Int,
    val location: Coordinates,
    val signature: String,
    val proofs: List<Proof>
)