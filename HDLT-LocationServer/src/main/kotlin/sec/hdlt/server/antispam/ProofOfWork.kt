package sec.hdlt.server.antispam

import sec.hdlt.protos.server.Report
import java.nio.ByteBuffer

data class ProofOfWork(
    val data: String,
    val hash: String,
    val nonce: Long
) {
    fun toByteArrayList(): MutableList<ByteArray> {
        return mutableListOf(
            data.toByteArray(),
            "SHA256".toByteArray(),
            ByteBuffer.allocate(8).putLong(nonce).array() //for nonce
        )
    }

    fun toGrpcProof(): Report.ProofOfWork {
        return Report.ProofOfWork.newBuilder().apply {
            data = this@ProofOfWork.data
            hash = this@ProofOfWork.hash
            nonce = this@ProofOfWork.nonce
        }.build()
    }

    companion object {
        fun fromGrpcProof(proof: Report.ProofOfWork): ProofOfWork {
            return ProofOfWork(
                proof.data,
                proof.hash,
                proof.nonce
            )
        }
    }
}

fun Report.ProofOfWork.toProofOfWork(): ProofOfWork {
    return ProofOfWork(data, hash, nonce)
}