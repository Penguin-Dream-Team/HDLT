package sec.hdlt.user.domain

import kotlinx.serialization.Serializable

@Serializable
data class LocationRequest(val id: Int, val epoch: Int, val signature: String)