package sec.hdlt.server.domain

import kotlinx.serialization.Serializable

@Serializable
data class WitnessResponse(val proofs: List<Proof>, val signature: String)

