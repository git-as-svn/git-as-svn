package svnserver.config;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.VcsRepository;

import javax.xml.bind.annotation.XmlSeeAlso;
import java.io.IOException;

/**
 * Repository configuration.
 *
 * @author a.navrotskiy
 */
@XmlSeeAlso({
    GitRepositoryConfig.class
})
public interface RepositoryConfig {
  @NotNull
  VcsRepository create() throws IOException, SVNException;
}
