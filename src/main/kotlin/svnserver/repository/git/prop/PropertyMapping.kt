/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop

import java.util.*

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
object PropertyMapping {
    private val parserByFile = TreeMap<String, GitPropertyFactory>()
    fun getFactory(fileName: String): GitPropertyFactory? {
        return parserByFile[fileName]
    }

    val registeredFiles: Collection<String>
        get() {
            return Collections.unmodifiableSet(parserByFile.keys)
        }

    init {
        arrayOf(
            GitAttributesFactory(),
            GitTortoiseFactory(),
            GitIgnoreFactory()
        ).forEach {
            val oldParser: GitPropertyFactory? = parserByFile.put(it.fileName, it)
            if (oldParser != null) {
                throw RuntimeException("Found two classes mapped for same file: " + oldParser.javaClass + " and " + it)
            }
        }
    }
}
