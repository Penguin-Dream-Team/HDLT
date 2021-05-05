package sec.hdlt.user.dto

import kotlinx.serialization.Serializable

/**
 * Object to request location info at given epoch
 */
@Serializable
data class LocationRequest(val id: Int, val epoch: Int, val signature: String)