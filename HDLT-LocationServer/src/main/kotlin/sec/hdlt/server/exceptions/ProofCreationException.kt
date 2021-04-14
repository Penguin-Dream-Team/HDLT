package sec.hdlt.server.exceptions

data class ProofCreationException(val report: Int, val prover: Int) :
    HDLTException("Failed to create a proof for report $report for prover $prover")
