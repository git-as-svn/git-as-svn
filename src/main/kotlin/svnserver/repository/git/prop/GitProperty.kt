/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop

import org.eclipse.jgit.lib.FileMode

/**
 * Interface for mapping git file to subversion attributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
interface GitProperty {
    /**
     * Update file properties on element.
     *
     * @param props Properties.
     */
    fun apply(props: MutableMap<String, String>)

    /**
     * Create GitProperty for child element.
     *
     * @param name Child node name.
     * @param mode Child node type.
     * @return Child property modifier or null, if this property is not affected for childs.
     */
    fun createForChild(name: String, mode: FileMode): GitProperty?

    /**
     * Get overrided filter name.
     *
     * @return Filter name.
     */
    val filterName: String?

    companion object {
        fun joinProperties(parentProps: Array<GitProperty>, entryName: String, fileMode: FileMode, entryProps: Array<GitProperty>): Array<GitProperty> {
            if (parentProps.isEmpty()) {
                return entryProps
            }
            val joined: Array<GitProperty?> = arrayOfNulls(parentProps.size + entryProps.size)
            var index = 0
            for (parentProp in parentProps) {
                val prop: GitProperty? = parentProp.createForChild(entryName, fileMode)
                if (prop != null) {
                    joined[index] = prop
                    index++
                }
            }
            System.arraycopy(entryProps, 0, joined, index, entryProps.size)
            return (if (index == parentProps.size) joined else joined.copyOf(index + entryProps.size)) as Array<GitProperty>
        }
    }
}
