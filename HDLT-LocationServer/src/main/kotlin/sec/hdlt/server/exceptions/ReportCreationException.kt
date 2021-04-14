package sec.hdlt.server.exceptions

data class ReportCreationException(val user: Int, val epoch: Int) :
    HDLTException("Failed to create a report for user $user in epoch $epoch")
