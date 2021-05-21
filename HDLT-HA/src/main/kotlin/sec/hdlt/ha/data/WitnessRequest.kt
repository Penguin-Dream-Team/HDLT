package sec.hdlt.ha.data

import kotlinx.serialization.Serializable

@Serializable
data class WitnessRequest(val userId: Int, val epochs: List<Int>,  val signature: String)