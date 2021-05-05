package sec.hdlt.user.dto

import kotlinx.serialization.Serializable
import sec.hdlt.user.domain.Coordinates

/**
 * Object for server response to request of location info at given epoch
 */
@Serializable
data class LocationResponse (val id: Int, val epoch: Int, val coords: Coordinates, val serverInfo: String, val signature: String, val proofs: List<ProofDto>)