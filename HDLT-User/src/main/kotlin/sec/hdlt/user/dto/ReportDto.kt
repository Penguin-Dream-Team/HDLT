package sec.hdlt.user.dto

import kotlinx.serialization.Serializable
import sec.hdlt.user.domain.Coordinates

/**
 * Report object used to be transferred between user and location server
 */
@Serializable
data class ReportDto(val id: Int, val epoch: Int, val location: Coordinates, val signature: String, val proofs: List<ProofDto>)