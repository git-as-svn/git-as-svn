/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import org.slf4j.Logger
import svnserver.Loggers
import svnserver.VersionInfo
import svnserver.config.Config
import svnserver.config.serializer.ConfigSerializer
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Entry point.
 *
 * @author a.navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
object Main {
    private val log: Logger = Loggers.misc

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val cmd = CmdArgs()
        val jc = JCommander(cmd)
        jc.parse(*args)
        if (cmd.help) {
            jc.usage()
            return
        }
        if (!cmd.showConfig) log.info("git-as-svn version: {}", VersionInfo.versionInfo)
        if (cmd.showVersion) return

        // Load config
        val serializer = ConfigSerializer()
        val configFile: Path = cmd.configuration.toAbsolutePath()
        val config: Config = serializer.load(configFile)
        if (cmd.testConfig) {
            log.info("the configuration file {} syntax is ok", configFile)
            log.info("configuration file {} test is successful", configFile)
            return
        }
        if (cmd.showConfig) {
            println("# configuration file $configFile:")
            println(serializer.dump(config))
            return
        }
        val server = SvnServer(configFile.parent, config)
        server.start()
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                server.shutdown(config.shutdownTimeout)
            } catch (e: Exception) {
                log.error("Can't shutdown correctly", e)
            }
        })
        server.join()
    }

    private class CmdArgs {
        @Parameter(names = ["-?", "-h", "--help"], description = "this help", help = true)
        var help: Boolean = false

        @Parameter(names = ["-c", "--config"], description = "set configuration file")
        var configuration: Path = Paths.get("/etc/git-as-svn/git-as-svn.conf")

        @Parameter(names = ["-t"], description = "test configuration and exit")
        var testConfig: Boolean = false

        @Parameter(names = ["-T"], description = "test configuration, dump it and exit")
        var showConfig: Boolean = false

        @Parameter(names = ["-v", "--version"], description = "show version and exit", help = true)
        var showVersion: Boolean = false
    }
}
