package sec.hdlt.ha.data

import kotlinx.serialization.Serializable

@Serializable
data class EpochLocationResponse(val users: List<Int>, val coords: Coordinates, val epoch: Int, val signature: String)