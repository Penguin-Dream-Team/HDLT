package sec.hdlt.server.exceptions

data class UserReportNotFoundException(val user: Int, val epoch: Int) :
    HDLTException("Report for user $user in epoch $epoch not found")
