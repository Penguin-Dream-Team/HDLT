package sec.hdlt.user.dto

import kotlinx.serialization.Serializable

/**
 * Proof object to be transferred between user and server
 */
@Serializable
data class ProofDto(val requester: Int, val prover: Int, val epoch: Int, val signature: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProofDto

        if (requester != other.requester) return false
        if (prover != other.prover) return false
        if (epoch != other.epoch) return false
        if (signature != other.signature) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requester
        result = 31 * result + prover
        result = 31 * result + epoch
        return result
    }
}
