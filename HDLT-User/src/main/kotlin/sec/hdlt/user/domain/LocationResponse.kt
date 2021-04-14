package sec.hdlt.user.domain

import kotlinx.serialization.Serializable

@Serializable
data class LocationResponse (val id: Int, val epoch: Int, val coords: Coordinates, val signature: String)