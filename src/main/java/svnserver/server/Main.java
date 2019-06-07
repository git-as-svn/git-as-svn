/*
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
import svnserver.Loggers;
import svnserver.VersionInfo;
import svnserver.config.Config;
import svnserver.config.serializer.ConfigSerializer;

import java.io.File;

/**
 * Entry point.
 *
 * @author a.navrotskiy
 */
public class Main {
  @NotNull
  private static final Logger log = Loggers.misc;

  public static void main(@NotNull String[] args) throws Exception {
    log.info("git-as-svn version: {}", VersionInfo.getVersionInfo());
    final CmdArgs cmd = new CmdArgs();
    final JCommander jc = new JCommander(cmd);
    jc.parse(args);
    if (cmd.help) {
      jc.usage();
      return;
    }
    // Load config
    ConfigSerializer serializer = new ConfigSerializer();
    Config config = serializer.load(cmd.configuration);
    if (cmd.showConfig) {
      log.info("Actual config:\n{}", serializer.dump(config));
    }
    final SvnServer server = new SvnServer(cmd.configuration.getAbsoluteFile().getParentFile(), config);
    server.start();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        server.shutdown(config.getShutdownTimeout());
      } catch (Exception e) {
        log.error("Can't shutdown correctly", e);
      }
    }));
    server.join();
  }

  private static class CmdArgs {
    @SuppressWarnings("NullableProblems")
    @Parameter(names = {"-c", "--config"}, description = "Configuration file name", required = true)
    @NotNull
    private File configuration;

    @Parameter(names = {"-s", "--show-config"}, description = "Show actual configuration on start")
    private boolean showConfig = false;

    @Parameter(names = {"-h", "--help"}, description = "Show help", help = true)
    private boolean help = false;
  }
}
