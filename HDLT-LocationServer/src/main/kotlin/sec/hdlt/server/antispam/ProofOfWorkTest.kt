package sec.hdlt.server.antispam

fun main() {
    val proof = proofOfWork(createProofOfWorkRequest("Hello"))

    proof?.let {
        val request = it.toGrpcProof()
        val received = ProofOfWork.fromGrpcProof(request)
        println(verifyProofOfWork(received))
    }
}
