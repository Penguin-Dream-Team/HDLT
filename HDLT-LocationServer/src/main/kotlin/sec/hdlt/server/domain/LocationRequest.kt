package sec.hdlt.server.domain

import kotlinx.serialization.Serializable

@Serializable
data class LocationRequest (val id: Int, val epoch: Int, val signature: String)