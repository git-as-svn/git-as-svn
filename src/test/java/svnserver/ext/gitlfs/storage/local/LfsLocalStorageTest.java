/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.local;

import com.google.common.io.CharStreams;
import org.eclipse.jgit.util.Holder;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestHelper;
import svnserver.SvnTestServer;
import svnserver.TemporaryOutputStream;
import svnserver.TestHelper;
import svnserver.auth.User;
import svnserver.ext.gitlfs.config.LocalLfsConfig;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ConcurrentSkipListMap;

import static svnserver.server.SvnFilePropertyTest.propsBinary;

/**
 * Simple test for LfsLocalStorage.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsLocalStorageTest {
  @DataProvider(name = "compressProvider")
  public static Object[][] compressProvider() {
    return new Object[][]{
        {true},
        {false},
    };
  }

  @Test
  public void commitToLocalLFS() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty(null, false, SvnTestServer.LfsMode.Local)) {
      final SVNRepository svnRepository = server.openSvnRepository();
      SvnTestHelper.createFile(svnRepository, ".gitattributes", "* -text\n*.txt filter=lfs diff=lfs merge=lfs -text", null);

      final byte[] data = bigFile();

      SvnTestHelper.createFile(svnRepository, "1.txt", data, propsBinary);
      SvnTestHelper.checkFileContent(svnRepository, "1.txt", data);

      final Holder<SVNLock> lockHolder = new Holder<>(null);
      svnRepository.lock(Collections.singletonMap("1.txt", svnRepository.getLatestRevision()), null, false, new ISVNLockHandler() {
        @Override
        public void handleLock(String path, SVNLock lock, SVNErrorMessage error) {
          Assert.assertEquals(path, "/1.txt");
          lockHolder.set(lock);
        }

        @Override
        public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) {
          Assert.fail();
        }
      });
      final SVNLock lock = lockHolder.get();
      Assert.assertNotNull(lock);
      Assert.assertEquals(lock.getPath(), "/1.txt");
      Assert.assertNotNull(lock.getID());
      Assert.assertEquals(lock.getOwner(), SvnTestServer.USER_NAME);
    }
  }

  @NotNull
  public static byte[] bigFile() {
    final byte[] data = new byte[TemporaryOutputStream.MAX_MEMORY_SIZE * 2];
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) (i % 256);
    }
    return data;
  }

  @Test(dataProvider = "compressProvider")
  public void simple(boolean compress) throws IOException {
    final File tempDir = TestHelper.createTempDir("git-as-svn");
    try {
      LfsLocalStorage storage = new LfsLocalStorage(new ConcurrentSkipListMap<>(), LocalLfsConfig.LfsLayout.TwoLevels, new File(tempDir, "data"), new File(tempDir, "meta"), compress);
      // Check file is not exists
      Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1));

      // Write new file
      try (final LfsWriter writer = storage.getWriter(User.getAnonymous())) {
        writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      }

      // Read old file.
      final LfsReader reader = storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1);
      Assert.assertNotNull(reader);
      Assert.assertEquals("9fe77772b085e3533101d59d33a51f19", reader.getMd5());
      Assert.assertEquals(15, reader.getSize());

      try (final InputStream stream = reader.openStream()) {
        Assert.assertEquals(CharStreams.toString(new InputStreamReader(stream, StandardCharsets.UTF_8)), "Hello, world!!!");
      }
    } finally {
      TestHelper.deleteDirectory(tempDir);
    }
  }

  @Test(dataProvider = "compressProvider")
  public void nometa(boolean compress) throws IOException {
    final File tempDir = TestHelper.createTempDir("git-as-svn");
    try {
      LfsLocalStorage storage = new LfsLocalStorage(new ConcurrentSkipListMap<>(), LocalLfsConfig.LfsLayout.GitLab, new File(tempDir, "data"), null, compress);
      // Check file is not exists
      Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1));

      // Write new file
      try (final LfsWriter writer = storage.getWriter(User.getAnonymous())) {
        writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      }

      // Read old file.
      final LfsReader reader = storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1);
      Assert.assertNotNull(reader);
      Assert.assertNull(reader.getMd5());
      Assert.assertEquals(15, reader.getSize());

      try (final InputStream stream = reader.openStream()) {
        Assert.assertEquals(CharStreams.toString(new InputStreamReader(stream, StandardCharsets.UTF_8)), "Hello, world!!!");
      }
    } finally {
      TestHelper.deleteDirectory(tempDir);
    }
  }

  @Test(dataProvider = "compressProvider")
  public void alreadyAdded(boolean compress) throws IOException {
    final File tempDir = TestHelper.createTempDir("git-as-svn");
    try {
      LfsLocalStorage storage = new LfsLocalStorage(new ConcurrentSkipListMap<>(), LocalLfsConfig.LfsLayout.TwoLevels, new File(tempDir, "data"), new File(tempDir, "meta"), compress);
      // Check file is not exists
      Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1));

      // Write new file
      try (final LfsWriter writer = storage.getWriter(User.getAnonymous())) {
        writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      }

      // Write new file
      try (final LfsWriter writer = storage.getWriter(User.getAnonymous())) {
        writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      }
    } finally {
      TestHelper.deleteDirectory(tempDir);
    }
  }
}
