package sec.hdlt.server.domain

import kotlinx.serialization.Serializable

@Serializable
data class LocationResponse (val id: Int, val epoch: Int, val coords: Coordinates, val serverInfo: String, val proofs: List<Proof>, var signature: String)
