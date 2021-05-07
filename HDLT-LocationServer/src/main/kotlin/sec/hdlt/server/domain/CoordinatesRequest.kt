package sec.hdlt.server.domain

import kotlinx.serialization.Serializable

@Serializable
data class CoordinatesRequest(val coords: Coordinates, val epoch: Int, val signature: String)