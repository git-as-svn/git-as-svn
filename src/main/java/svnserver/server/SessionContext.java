package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import svnserver.StringHelper;
import svnserver.auth.ACL;
import svnserver.auth.User;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.VcsFile;
import svnserver.repository.VcsRepository;
import svnserver.server.msg.ClientInfo;
import svnserver.server.step.Step;

import java.io.IOException;
import java.util.*;

/**
 * SVN client session context.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class SessionContext {
  @NotNull
  private final SvnServerParser parser;
  @NotNull
  private final SvnServerWriter writer;
  @NotNull
  private final Deque<Step> stepStack = new ArrayDeque<>();
  @NotNull
  private final SvnServer server;
  @NotNull
  private final SVNURL baseUrl;
  @NotNull
  private final Set<String> capabilities;
  @NotNull
  private final User user;
  @NotNull
  private String parent;

  public SessionContext(@NotNull SvnServerParser parser,
                        @NotNull SvnServerWriter writer,
                        @NotNull SvnServer server,
                        @NotNull String baseUrl,
                        @NotNull ClientInfo clientInfo,
                        @NotNull User user) throws SVNException {
    this.parser = parser;
    this.writer = writer;
    this.server = server;
    this.user = user;
    this.baseUrl = SVNURL.parseURIEncoded(baseUrl + (baseUrl.endsWith("/") ? "" : ""));
    this.parent = getRelative(SVNURL.parseURIEncoded(clientInfo.getUrl()));
    this.capabilities = new HashSet<>(Arrays.asList(clientInfo.getCapabilities()));
  }

  public boolean hasCapability(@NotNull String capability) {
    return capabilities.contains(capability);
  }

  public void setParent(@NotNull String parent) {
    this.parent = parent;
  }

  @NotNull
  public String getRepositoryPath(@Nullable String localPath) throws SVNException {
    if ((localPath != null) && localPath.startsWith("svn://")) {
      return getRelative(SVNURL.parseURIEncoded(localPath));
    }
    return StringHelper.joinPath(parent, localPath);
  }

  private String getRelative(@NotNull SVNURL url) throws SVNException {
    final String root = baseUrl.getPath();
    final String path = url.getPath();
    if (!path.startsWith(root)) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Invalid relative path: " + path + " (base: " + root + ")"));
    }
    if (root.length() == path.length()) {
      return "";
    }
    final boolean hasSlash = root.endsWith("/");
    if ((!hasSlash) && (path.charAt(root.length()) != '/')) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Invalid relative path: " + path + " (base: " + root + ")"));
    }
    return StringHelper.normalize(path.substring(root.length()));
  }

  @NotNull
  public User getUser() {
    return user;
  }

  @NotNull
  public VcsRepository getRepository() {
    return server.getRepository();
  }

  @NotNull
  public ACL getAcl() {
    return server.getAcl();
  }

  @NotNull
  public SvnServerParser getParser() {
    return parser;
  }

  @NotNull
  public SvnServerWriter getWriter() {
    return writer;
  }

  public void push(@NotNull Step step) {
    stepStack.push(step);
  }

  @Nullable
  public Step poll() {
    return stepStack.poll();
  }

  /**
   * Get repository file.
   *
   * @param rev        Target revision.
   * @param targetPath Target path or url.
   * @return Return file object.
   * @throws SVNException
   * @throws IOException
   */
  @Nullable
  public VcsFile getFile(int rev, String targetPath) throws SVNException, IOException {
    return getRepository().getRevisionInfo(rev).getFile(getRepositoryPath(targetPath));
  }
}
