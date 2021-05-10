package sec.hdlt.server.exceptions

data class SpamException(val user: Int) :
    HDLTException("Received to many requests report from user $user")
