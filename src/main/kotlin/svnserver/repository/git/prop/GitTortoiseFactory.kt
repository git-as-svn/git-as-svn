package svnserver.repository.git.prop

import svnserver.repository.git.RepositoryFormat
import java.io.IOException
import java.io.InputStream

class GitTortoiseFactory : GitPropertyFactory {
    override val fileName: String
        get() {
            return ".tgitconfig"
        }

    @Throws(IOException::class)
    override fun create(stream: InputStream, format: RepositoryFormat): Array<GitProperty> {
        return arrayOf(GitTortoise.parseConfig(stream))
    }
}
