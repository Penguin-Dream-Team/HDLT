package sec.hdlt.server.exceptions

data class DuplicateReportException(val user: Int, val epoch: Int) :
    HDLTException("Received duplicate report for user $user in epoch $epoch")
