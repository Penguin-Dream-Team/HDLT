package sec.hdlt.server.data

import kotlinx.serialization.Serializable

@Serializable
data class CoordinatesRequest(val coords: Coordinates, val epoch: Int, val signature: String)