package sec.hdlt.server.data

import kotlinx.serialization.Serializable

@Serializable
data class Proof(val requester: Int, val prover: Int, val epoch: Int, val signature: String)
