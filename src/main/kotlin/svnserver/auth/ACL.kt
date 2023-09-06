/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth

import org.apache.commons.collections4.trie.PatriciaTrie
import org.eclipse.collections.api.block.function.primitive.BooleanFunction
import org.eclipse.jetty.util.TopologicalSort
import svnserver.StringHelper
import svnserver.UserType
import svnserver.repository.RepositoryMapping
import svnserver.repository.VcsAccess
import java.util.*
import kotlin.collections.HashSet

/**
 * This ACL reuses SVN's authz syntax as much as possible: http://svnbook.red-bean.com/nightly/en/svn.serverconfig.pathbasedauthz.html
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class ACL(contextName: String, group2users: Map<String, Array<String>>, branchPath2Member2AccessMode: Map<String, Map<String, String?>>) : VcsAccess {
    private val user2groups = PatriciaTrie<HashSet<String>>()
    private val anonymousGroups = HashSet<String>()
    private val authenticatedGroups = EnumMap<UserType, HashSet<String>>(UserType::class.java)
    private val path2branch2acl = TreeMap<String, PatriciaTrie<HashMap<ACLEntry, AccessMode>>>()

    constructor(group2users: Map<String, Array<String>>, branchPath2Member2AccessMode: Map<String, Map<String, String?>>) : this("", group2users, branchPath2Member2AccessMode)

    private fun addAclEntry(contextName: String, branch: String, path: String, entryString: String, accessMode: AccessMode, allGroups: Set<String>) {
        val entry: ACLEntry = when {
            entryString == EveryoneMarker -> EveryoneACLEntry.Instance
            entryString == AnonymousMarker -> AnonymousACLEntry.Instance
            entryString == AuthenticatedMarker -> AuthenticatedACLEntry.Instance
            entryString.startsWith(AuthenticatedPrefix) -> {
                val userType = UserType.valueOf(entryString.substring(AuthenticatedPrefix.length))
                UserTypeEntry(userType)
            }
            entryString.startsWith(GroupPrefix) -> {
                val group = entryString.substring(GroupPrefix.length)
                if (!allGroups.contains(group)) throw IllegalArgumentException(String.format("[%s] ACL entry %s uses unknown group %s: ", contextName, path, group))
                GroupACLEntry(group)
            }
            else -> UserACLEntry(entryString)
        }
        require(path2branch2acl.computeIfAbsent(StringHelper.normalizeDir(path)) { PatriciaTrie<HashMap<ACLEntry, AccessMode>>() }.computeIfAbsent(branch) { HashMap() }.put(entry, accessMode) == null) { String.format("[%s] Duplicate ACL entry %s: %s", contextName, path, entryString) }
    }

    override fun canRead(user: User, branch: String, path: String): Boolean {
        return doCheck(user, branch, path) { obj: AccessMode -> obj.allowsRead() }
    }

    override fun canWrite(user: User, branch: String, path: String): Boolean {
        return doCheck(user, branch, path) { obj: AccessMode -> obj.allowsWrite() }
    }

    private fun doCheck(user: User, branch: String, path: String, checker: BooleanFunction<AccessMode>): Boolean {
        var pathToCheck = path
        while (true) {
            val pathEntry: Map.Entry<String, PatriciaTrie<HashMap<ACLEntry, AccessMode>>> = RepositoryMapping.getMapped(path2branch2acl, pathToCheck) ?: break
            for (b in arrayOf(branch, NoBranch)) {
                val branchPathEntry = pathEntry.value[b] ?: continue
                val checkResult = check(user, checker, branchPathEntry)
                if (checkResult == CheckResult.Deny) return false else if (checkResult == CheckResult.Allow) return true
            }

            // We didn't find a matching entry, so need to go up in hierarchy
            val prevSlash = pathEntry.key.lastIndexOf('/', pathEntry.key.length - 2)
            if (prevSlash < 0) break
            pathToCheck = pathEntry.key.substring(0, prevSlash)
        }
        return false
    }

    private fun check(user: User, checker: BooleanFunction<AccessMode>, entries: Map<ACLEntry, AccessMode>): CheckResult {
        var result = CheckResult.Unspecified
        for ((key, value) in entries) {
            if (!key.matches(user)) continue
            if (checker.booleanValueOf(value)) // Explicit allow is stronger than explicit deny
                return CheckResult.Allow
            result = CheckResult.Deny
        }
        return result
    }

    private enum class CheckResult {
        Unspecified, Allow, Deny
    }

    private enum class AccessMode {
        none, r, rw;

        fun allowsRead(): Boolean {
            return this != none
        }

        fun allowsWrite(): Boolean {
            return this == rw
        }

        companion object {
            fun fromString(value: String?): AccessMode {
                return if (value == null || value.isEmpty()) none else valueOf(value)
            }
        }
    }

    private enum class EveryoneACLEntry : ACLEntry {
        Instance;

        override fun matches(user: User): Boolean {
            return true
        }
    }

    private enum class AnonymousACLEntry : ACLEntry {
        Instance;

        override fun matches(user: User): Boolean {
            return user.isAnonymous
        }
    }

    private enum class AuthenticatedACLEntry : ACLEntry {
        Instance;

        override fun matches(user: User): Boolean {
            return !user.isAnonymous
        }
    }

    private interface ACLEntry {
        fun matches(user: User): Boolean
    }

    private class UserTypeEntry(private val userType: UserType) : ACLEntry {
        override fun matches(user: User): Boolean {
            return !user.isAnonymous && user.type == userType
        }
    }

    private class UserACLEntry(private val user: String) : ACLEntry {
        override fun matches(user: User): Boolean {
            return !user.isAnonymous && user.username == this.user
        }

        override fun hashCode(): Int {
            return user.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return other is UserACLEntry && user == other.user
        }
    }

    private inner class GroupACLEntry(private val group: String) : ACLEntry {
        override fun matches(user: User): Boolean {
            if (user.isAnonymous) return anonymousGroups.contains(group)
            return if (authenticatedGroups.getOrDefault(user.type, emptySet()).contains(group)) true else user2groups.getOrDefault(user.username, emptySet()).contains(group)
        }

        override fun hashCode(): Int {
            return group.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return other is GroupACLEntry && group == other.group
        }
    }

    companion object {
        const val EveryoneMarker = "*"
        const val AnonymousMarker = "\$anonymous"
        const val AuthenticatedMarker = "\$authenticated"
        private const val AuthenticatedPrefix = "$AuthenticatedMarker:"
        private const val GroupPrefix = "@"

        /**
         * Special marker that means "ACL entry is not bound to specific branch".
         */
        private const val NoBranch = ""
        private const val BranchPathSeparator = ':'
        private fun expandGroups(contextName: String, groups: Map<String, Array<String>>): Map<String, MutableSet<String>> {
            val sorted = groups.keys.toTypedArray()
            val topoSort = TopologicalSort<String>()
            for ((key, value) in groups) for (member in value) if (member.startsWith(GroupPrefix)) topoSort.addDependency(key, member.substring(GroupPrefix.length))

            // This will throw exception in case of cycles, this is what we want
            topoSort.sort(sorted)
            val group2Users: MutableMap<String, MutableSet<String>> = HashMap()
            for (group in sorted) {
                for (member in groups[group]!!) {
                    val expandedMembers = group2Users.computeIfAbsent(group) { HashSet() }
                    if (member.startsWith(GroupPrefix)) {
                        val subgroup = member.substring(GroupPrefix.length)
                        val subgroupMembers: MutableSet<String> = group2Users[subgroup] ?: throw IllegalArgumentException(String.format("[%s] Group %s references nonexistent group %s", contextName, group, subgroup))
                        expandedMembers.addAll(subgroupMembers)
                    } else expandedMembers.add(member)
                }
            }
            return group2Users
        }
    }

    init {
        val group2Users = expandGroups(contextName, group2users)
        for ((groupName, value) in group2Users) {
            for (member in value) {
                when {
                    member == AnonymousMarker -> anonymousGroups.add(groupName)
                    member == AuthenticatedMarker -> {
                        for (userType in UserType.entries) authenticatedGroups.computeIfAbsent(userType) { HashSet() }.add(groupName)
                    }
                    member.startsWith(AuthenticatedPrefix) -> {
                        val userType = UserType.valueOf(member.substring(AuthenticatedPrefix.length))
                        authenticatedGroups.computeIfAbsent(userType) { HashSet() }.add(groupName)
                    }
                    else -> user2groups.computeIfAbsent(member) { HashSet() }.add(groupName)
                }
            }
        }
        for ((branchPath, value) in branchPath2Member2AccessMode) {
            val branchSep = branchPath.indexOf(BranchPathSeparator)
            var branch: String
            var path: String
            if (branchSep < 0) {
                branch = NoBranch
                path = branchPath
            } else {
                require(branchSep != 0) { String.format("[%s] Branch name in ACL entry must not be empty: %s", contextName, branchPath) }
                branch = branchPath.substring(0, branchSep)
                path = if (branchSep < branchPath.length - 1) branchPath.substring(branchSep + 1) else "" // Empty path is invalid and will be properly error-reported by code below
            }
            if (!path.startsWith("/")) throw IllegalArgumentException(String.format("[%s] Path in ACL entry must start with slash (/): %s", contextName, branchPath))
            if (path.endsWith("/") && path.length > 1) throw IllegalArgumentException(String.format("[%s] Path in ACL entry must not end with slash (/): %s", contextName, branchPath))
            if (value.isEmpty()) throw IllegalArgumentException(String.format("[%s] ACL entry is empty: %s", contextName, branchPath))
            for ((key, value1) in value) {
                val accessMode = AccessMode.fromString(value1)
                addAclEntry(contextName, branch, path, key, accessMode, group2users.keys)
            }
        }
    }
}
