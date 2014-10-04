/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import org.yaml.snakeyaml.Yaml;
import svnserver.config.Config;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Entry point.
 *
 * @author a.navrotskiy
 */
public class Main {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(SvnServer.class);

  public static void main(@NotNull String[] args) throws IOException, SVNException, InterruptedException {
    final CmdArgs cmd = new CmdArgs();
    final JCommander jc = new JCommander(cmd);
    jc.parse(args);
    if (cmd.help) {
      jc.usage();
      return;
    }
    // Load config
    Yaml yaml = new Yaml();
    Config config;
    try (
        InputStream stream = new FileInputStream(cmd.configuration);
        Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)
    ) {
      config = yaml.loadAs(reader, Config.class);
    }
    if (cmd.showConfig) {
      log.info("Actual config:\n{}", yaml.dump(config));
    }
    final SvnServer server = new SvnServer(config);
    server.start();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        server.shutdown(config.getShutdownTimeout());
      } catch (IOException | InterruptedException e) {
        log.error("Can't shutdown correctly", e);
      }
    }));
    server.join();
  }

  public static class CmdArgs {
    @Parameter(names = {"-c", "--config"}, description = "Configuration file name", required = true)
    @NotNull
    private File configuration;

    @Parameter(names = {"-s", "--show-config"}, description = "Show actual configuration on start")
    private boolean showConfig = false;

    @Parameter(names = {"-h", "--help"}, description = "Show help", help = true)
    private boolean help = false;
  }

}
