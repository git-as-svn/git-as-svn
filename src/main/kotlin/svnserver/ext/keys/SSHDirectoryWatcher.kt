/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.keys

import svnserver.*
import svnserver.context.Shared
import java.io.IOException
import java.nio.file.*
import java.util.*

/**
 * SSHDirectoryWatcher.
 *
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
internal class SSHDirectoryWatcher(config: KeysConfig, private val mapper: KeysMapper?) : Thread(), Shared {
    private var watchService: WatchService
    private var basePath: Path
    private var realSSHPath: Path
    private val originalAppPath: String = config.originalAppPath
    private val svnServePath: String = config.svnservePath
    override fun run() {
        try {
            // Run this first.
            mungeAuthorizedKeys()
            basePath.register(watchService, *KINDS)
            while (!isInterrupted) {
                val key = watchService.take()
                if (isInterrupted) {
                    break
                }
                for (event in key.pollEvents()) {
                    val context = event.context() as? Path ?: continue
                    if (context.toString() != AUTHORIZED_KEYS) {
                        continue
                    }
                    // OK we're looking at authorized_keys - munge it!
                    mungeAuthorizedKeys()
                }
                if (!key.reset()) {
                    key.cancel()
                    break
                }
            }
        } catch (e: InterruptedException) {
            // noop
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    private fun mungeAuthorizedKeys() {
        val authPath = basePath.resolve(AUTHORIZED_KEYS)
        val realAuthPath = realSSHPath.resolve(AUTHORIZED_KEYS)
        log.info("Processing the authorized_keys file: {}", authPath.toString())
        val keysSet = HashSet<String>()
        Files.newBufferedReader(authPath).use { reader ->
            Files.newBufferedWriter(realAuthPath).use { writer ->
                reader.lines().map { s: String ->
                    if (s.contains(originalAppPath)) {
                        val indexOfKey = s.indexOf("key-")
                        keysSet.add(s.substring(indexOfKey, s.indexOf(' ', indexOfKey)))
                        return@map s.replace(originalAppPath, svnServePath)
                    } else {
                        return@map s
                    }
                }.forEach { s: String ->
                    try {
                        writer.write(s)
                        writer.write('\n'.code)
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }
                }
            }
        }
        log.info("Found {} keys", keysSet.size)

        // OK now we know about which keys are there.
        // So we tell our keys mapper...
        mapper?.setKeys(keysSet)
    }

    override fun close() {
        interrupt()
    }

    companion object {
        private val KINDS = arrayOf<WatchEvent.Kind<*>>(
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE
        )
        private const val AUTHORIZED_KEYS = "authorized_keys"
        private val log = Loggers.misc
    }

    init {
        try {
            basePath = Paths.get(config.shadowSSHDirectory).toAbsolutePath()
            realSSHPath = Paths.get(config.realSSHDirectory).toAbsolutePath()
            watchService = FileSystems.getDefault().newWatchService()
            start()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}
