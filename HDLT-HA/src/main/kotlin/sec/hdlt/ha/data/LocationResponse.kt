package sec.hdlt.ha.data

import kotlinx.serialization.Serializable

@Serializable
data class LocationResponse(val users: List<Int>, val coords: Coordinates, val epoch: Int, val serverInfo: String, val signature: String)