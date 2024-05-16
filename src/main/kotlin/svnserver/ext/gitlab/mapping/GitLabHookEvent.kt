/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.io.Reader

/**
 * GitLab hook JSON mapping.
 *
 *
 * https://gitlab.com/gitlab-org/gitlab-ce/blob/master/doc/system_hooks/system_hooks.md
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class GitLabHookEvent {
    @JsonProperty("event_name")
    val eventName: String? = null

    @JsonProperty("path_with_namespace")
    val pathWithNamespace: String? = null

    @JsonProperty("project_id")
    val projectId: Long? = null

    companion object {
        private val mapper = ObjectMapper()

        @Throws(IOException::class)
        fun parseEvent(reader: Reader): GitLabHookEvent {
            return mapper.readValue(reader, GitLabHookEvent::class.java)
        }
    }
}
