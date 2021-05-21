package sec.hdlt.ha.antispam

import sec.hdlt.ha.hash
import java.math.BigInteger
import java.util.*

val target: BigInteger = BigInteger.ONE.shiftLeft(256 - 24)

fun verifyProofOfWork(proofOfWork: ProofOfWork): Boolean {
    return verifyProofOfWork(proofOfWork, target)
}

fun verifyProofOfWork(proofOfWork: ProofOfWork, target: BigInteger): Boolean {
    val result = hash(proofOfWork.toByteArrayList(), proofOfWork.nonce)
    val hash = Base64.getEncoder().encodeToString(result)
    return BigInteger(1, result).compareTo(target) == -1 && hash == proofOfWork.hash
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
        workTarget = target
    )
}
