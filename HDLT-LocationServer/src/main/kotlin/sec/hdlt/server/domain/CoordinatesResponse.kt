package sec.hdlt.server.domain

import kotlinx.serialization.Serializable

@Serializable
data class CoordinatesResponse(val users: List<Int>, val coords: Coordinates, val epoch: Int, val signature: String)
