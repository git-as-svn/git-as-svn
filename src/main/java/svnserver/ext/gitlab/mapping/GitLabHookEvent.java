/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;

/**
 * GitLab hook JSON mapping.
 * <p>
 * https://gitlab.com/gitlab-org/gitlab-ce/blob/master/doc/system_hooks/system_hooks.md
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitLabHookEvent {
  @JsonProperty("event_name")
  private String eventName;
  @JsonProperty("path_with_namespace")
  private String pathWithNamespace;
  @JsonProperty("project_id")
  private Integer projectId;

  public String getEventName() {
    return eventName;
  }

  public String getPathWithNamespace() {
    return pathWithNamespace;
  }

  public Integer getProjectId() {
    return projectId;
  }

  @NotNull
  public static GitLabHookEvent parseEvent(@NotNull Reader reader) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(reader, GitLabHookEvent.class);
  }
}
