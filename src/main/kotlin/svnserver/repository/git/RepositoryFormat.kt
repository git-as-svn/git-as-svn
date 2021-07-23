package svnserver.repository.git

enum class RepositoryFormat(val revision: Int) {
    V4(4),
    V5_REMOVE_IMPLICIT_NATIVE_EOL(5);

    companion object {
        val Latest = V5_REMOVE_IMPLICIT_NATIVE_EOL
    }
}
