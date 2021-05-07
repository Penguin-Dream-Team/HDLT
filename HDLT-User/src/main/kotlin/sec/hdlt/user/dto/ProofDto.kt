package sec.hdlt.user.dto

import kotlinx.serialization.Serializable

/**
 * Proof object to be transferred between user and server
 */
@Serializable
data class ProofDto(val requester: Int, val prover: Int, val epoch: Int, val signature: String)
