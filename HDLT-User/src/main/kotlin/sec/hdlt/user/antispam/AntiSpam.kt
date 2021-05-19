package sec.hdlt.user.antispam

import sec.hdlt.user.hash
import java.math.BigInteger
import java.util.*

fun verifyProofOfWork(proofOfWork: ProofOfWork): Boolean {
    val hash = Base64.getEncoder().encodeToString(hash(proofOfWork.toByteArrayList(), proofOfWork.nonce))
    return hash == proofOfWork.hash
}

fun proofOfWork(input: ProofOfWorkRequest): ProofOfWork? {
    for (nonce in 0..Long.MAX_VALUE) {
        val result = hash(input.toByteArrayList(), nonce)

        if (BigInteger(1, result).compareTo(input.workTarget) == -1) {
            return input.toProofWork(Base64.getEncoder().encodeToString(result), nonce)
        }
    }

    return null
}

fun createProofOfWorkRequest(data: String): ProofOfWorkRequest {
    return ProofOfWorkRequest(
        data = data,
        workTarget = BigInteger.ONE.shiftLeft(256 - 24)
    )
}