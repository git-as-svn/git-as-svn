/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config.serializer

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.introspector.BeanAccess
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import svnserver.auth.cache.CacheUserDBConfig
import svnserver.auth.combine.CombineUserDBConfig
import svnserver.auth.ldap.config.*
import svnserver.config.*
import svnserver.ext.gitea.auth.GiteaUserDBConfig
import svnserver.ext.gitea.config.GiteaConfig
import svnserver.ext.gitea.mapping.GiteaMappingConfig
import svnserver.ext.gitlab.auth.GitLabUserDBConfig
import svnserver.ext.gitlab.config.FileLfsMode
import svnserver.ext.gitlab.config.GitLabConfig
import svnserver.ext.gitlab.config.HttpLfsMode
import svnserver.ext.gitlab.mapping.GitLabMappingConfig
import svnserver.ext.gitlfs.LocalLfsConfig
import svnserver.ext.keys.KeyUserDBConfig
import svnserver.ext.keys.KeysConfig
import svnserver.ext.web.config.ListenHttpConfig
import svnserver.ext.web.config.WebServerConfig
import svnserver.repository.git.push.GitPushEmbeddedConfig
import svnserver.repository.git.push.GitPushNativeConfig
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Helper for parse/serialize configuration files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class ConfigSerializer {
    private val yaml: Yaml
    fun dump(config: Config): String {
        return yaml.dump(config)
    }

    @Throws(IOException::class)
    fun load(file: Path): Config {
        Files.newInputStream(file).use { stream -> return load(stream) }
    }

    private fun load(stream: InputStream): Config {
        return yaml.loadAs(stream, Config::class.java)
    }

    private class ConfigConstructor : Constructor() {
        init {
            for ((tag, klass) in configTypes.entries) {
                addTypeDescription(TypeDescription(klass, tag))
            }
        }
    }

    private class ConfigRepresenter : Representer() {
        init {
            for (entry in configTypes.entries) {
                addClassTag(entry.value, Tag(entry.key))
            }
        }
    }

    companion object {
        private val configTypes = mapOf(
            "!cacheUsers" to CacheUserDBConfig::class.java,
            "!combineUsers" to CombineUserDBConfig::class.java,
            "!ANONYMOUS" to LdapBindANONYMOUS::class.java,
            "!CRAMMD5" to LdapBindCRAMMD5::class.java,
            "!DIGESTMD5" to LdapBindDIGESTMD5::class.java,
            "!EXTERNAL" to LdapBindEXTERNAL::class.java,
            "!PLAIN" to LdapBindPLAIN::class.java,
            "!Simple" to LdapBindSimple::class.java,
            "!ldapUsers" to LdapUserDBConfig::class.java,
            "!config" to Config::class.java,
            "!localUsers" to LocalUserDBConfig::class.java,
            "!memoryCache" to MemoryCacheConfig::class.java,
            "!persistentCache" to PersistentCacheConfig::class.java,
            "!listMapping" to RepositoryListMappingConfig::class.java,
            "!submodules" to SharedSubmodulesConfig::class.java,
            "!giteaUsers" to GiteaUserDBConfig::class.java,
            "!gitea" to GiteaConfig::class.java,
            "!giteaMapping" to GiteaMappingConfig::class.java,
            "!gitlabUsers" to GitLabUserDBConfig::class.java,
            "!fileLfs" to FileLfsMode::class.java,
            "!gitlab" to GitLabConfig::class.java,
            "!httpLfs" to HttpLfsMode::class.java,
            "!gitlabMapping" to GitLabMappingConfig::class.java,
            "!localLfs" to LocalLfsConfig::class.java,
            "!sshKeys" to KeysConfig::class.java,
            "!sshKeyUsers" to KeyUserDBConfig::class.java,
            "!http" to ListenHttpConfig::class.java,
            "!web" to WebServerConfig::class.java,
            "!pushEmbedded" to GitPushEmbeddedConfig::class.java,
            "!pushNative" to GitPushNativeConfig::class.java,
        )
    }

    init {
        val options = DumperOptions()
        options.isPrettyFlow = true
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        yaml = Yaml(ConfigConstructor(), ConfigRepresenter(), options)
        yaml.setBeanAccess(BeanAccess.FIELD)
    }
}
