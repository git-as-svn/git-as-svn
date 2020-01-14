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

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point.
 *
 * @author a.navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class Main {
  @NotNull
  private static final Logger log = Loggers.misc;

  public static void main(@NotNull String[] args) throws Exception {
    final CmdArgs cmd = new CmdArgs();
    final JCommander jc = new JCommander(cmd);
    jc.parse(args);

    if (cmd.help) {
      jc.usage();
      return;
    }

    if (!cmd.showConfig)
      log.info("git-as-svn version: {}", VersionInfo.getVersionInfo());

    if (cmd.showVersion)
      return;

    // Load config
    ConfigSerializer serializer = new ConfigSerializer();
    final Path configFile = cmd.configuration.toAbsolutePath();

    Config config = serializer.load(configFile);

    if (cmd.testConfig) {
      log.info("the configuration file {} syntax is ok", configFile);
      log.info("configuration file {} test is successful", configFile);
      return;
    }

    if (cmd.showConfig) {
      System.out.println("# configuration file " + configFile + ":");
      System.out.println(serializer.dump(config));
      return;
    }

    final SvnServer server = new SvnServer(configFile.getParent(), config);
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

    @Parameter(names = {"-?", "-h", "--help"}, description = "this help", help = true)
    private boolean help = false;

    @Parameter(names = {"-c", "--config"}, description = "set configuration file")
    @NotNull
    private Path configuration = Paths.get("/etc/git-as-svn/git-as-svn.conf");

    @Parameter(names = {"-t"}, description = "test configuration and exit")
    private boolean testConfig = false;

    @Parameter(names = {"-T"}, description = "test configuration, dump it and exit")
    private boolean showConfig = false;

    @Parameter(names = {"-v", "--version"}, description = "show version and exit", help = true)
    private boolean showVersion = false;
  }
}
