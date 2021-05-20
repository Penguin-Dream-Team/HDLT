package sec.hdlt.user.dto

import kotlinx.serialization.Serializable

@Serializable
data class WitnessResponse(val proofs: List<ProofDto>, val signature: String) {
    override fun toString(): String {
        var result = ""
        proofs.forEach { proof ->
            result += "$proof\n"
        }
        return result
    }
}
