/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.mapping

import java.io.IOException
import java.nio.file.*
import java.util.*

/**
 * DirectoryWatcher.
 *
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
internal class DirectoryWatcher(path: String, mapping: DirectoryMapping) : Thread() {
    private val map = HashMap<WatchKey, Path>()
    private var basePath: Path
    private var watchService: WatchService
    private val mapping: DirectoryMapping

    @Throws(IOException::class)
    private fun addRepositories(parent: Path) {
        Files.newDirectoryStream(parent).use { stream ->
            for (path in stream) {
                if (isGitDirectory(path)) {
                    addRepository(parent.resolve(path))
                }
            }
        }
    }

    private fun addRepository(path: Path) {
        val pathName = path.fileName.toString()
        addRepository(path.parent.fileName.toString(), pathName.substring(0, pathName.length - 4))
    }

    private fun addRepository(owner: String, repo: String) {
        mapping.addRepository(owner, repo)
    }

    private fun removeRepositories(parent: Path) {
        removeRepositories(parent.fileName.toString())
    }

    private fun removeRepositories(owner: String) {
        mapping.removeRepositories(owner)
    }

    private fun removeRepository(path: Path) {
        val pathName = path.fileName.toString()
        removeRepository(path.parent.fileName.toString(), pathName.substring(0, pathName.length - 4))
    }

    private fun removeRepository(owner: String, repo: String) {
        mapping.removeRepository(owner, repo)
    }

    override fun run() {
        try {
            watchPath(basePath)
            watchSubDirectories(basePath)
            while (true) {
                val key = watchService.take()
                for (event in key.pollEvents()) {
                    handleEvent(event, key)
                }
                if (!key.reset()) {
                    val isBasePath = handleFailToReset(key)
                    if (map.isEmpty() || isBasePath) {
                        break
                    }
                }
            }
            for (k in map.keys) {
                k.cancel()
            }
            map.clear()
            watchService.close()
        } catch (ex: InterruptedException) {
            // noop
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
    }

    @Throws(IOException::class)
    private fun handleEvent(event: WatchEvent<*>, key: WatchKey) {
        val path = key.watchable() as Path
        val newPath = path.resolve(event.context() as Path)
        val isBasePathKey = path == basePath
        if (isBasePathKey) {
            val kind = event.kind()
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                watchPath(newPath)
                addRepositories(newPath)
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                // Remove key relating to old path name
                val it: MutableIterator<Map.Entry<WatchKey, Path>> = map.entries.iterator()
                while (it.hasNext()) {
                    val e = it.next()
                    val eKey = e.key
                    val ePath = e.value
                    if (ePath == newPath) {
                        eKey.cancel()
                        it.remove()
                    }
                }
                removeRepositories(newPath)
            }
        } else if (isGitDirectory(newPath)) {
            val kind = event.kind()
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                addRepository(newPath)
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                removeRepository(newPath)
            }
        }
    }

    private fun handleFailToReset(key: WatchKey): Boolean {
        val path = key.watchable() as Path
        key.cancel()
        val it: MutableIterator<Map.Entry<WatchKey, Path>> = map.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val eKey = e.key
            val ePath = e.value
            if (ePath.parent == path) {
                eKey.cancel()
                it.remove()
            }
        }
        return path == basePath
    }

    @Throws(IOException::class)
    private fun watchPath(path: Path): WatchKey {
        val key = path.register(watchService, *KINDS)
        map[key] = path
        return key
    }

    @Throws(IOException::class)
    private fun watchSubDirectories(path: Path) {
        Files.newDirectoryStream(path).use { stream ->
            for (directory in stream) {
                if (Files.isDirectory(directory)) {
                    watchPath(path.resolve(directory).toAbsolutePath())
                    addRepositories(directory)
                }
            }
        }
    }

    interface DirectoryMapping {
        fun addRepository(owner: String, repo: String)
        fun removeRepositories(owner: String)
        fun removeRepository(owner: String, repo: String)
    }

    companion object {
        private fun isGitDirectory(path: Path): Boolean {
            return Files.isDirectory(path) && path.toString().endsWith(".git")
        }

        private val KINDS = arrayOf<WatchEvent.Kind<*>>(
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE
        )
    }

    init {
        try {
            basePath = Paths.get(path).toAbsolutePath()
            watchService = FileSystems.getDefault().newWatchService()
            this.mapping = mapping
            start()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}
