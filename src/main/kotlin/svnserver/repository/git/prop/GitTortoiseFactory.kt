package svnserver.repository.git.prop

import java.io.IOException
import java.io.InputStream

class GitTortoiseFactory : GitPropertyFactory {
    override val fileName: String
        get() {
            return ".tgitconfig"
        }

    @Throws(IOException::class)
    override fun create(stream: InputStream): Array<GitProperty> {
        return arrayOf(GitTortoise.parseConfig(stream))
    }
}
