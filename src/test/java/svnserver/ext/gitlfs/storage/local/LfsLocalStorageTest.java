/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.local;

import com.google.common.io.CharStreams;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import svnserver.TestHelper;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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

  @Test(dataProvider = "compressProvider")
  public void simple(boolean compress) throws IOException {
    final File tempDir = TestHelper.createTempDir("git-as-svn");
    try {
      LfsLocalStorage storage = new LfsLocalStorage(new File(tempDir, "data"), new File(tempDir, "meta"), compress);
      // Check file is not exists
      Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308"));

      // Write new file
      try (final LfsWriter writer = storage.getWriter(null)) {
        writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      }

      // Read old file.
      final LfsReader reader = storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
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
  public void alreadyAdded(boolean compress) throws IOException {
    final File tempDir = TestHelper.createTempDir("git-as-svn");
    try {
      LfsLocalStorage storage = new LfsLocalStorage(new File(tempDir, "data"), new File(tempDir, "meta"), compress);
      // Check file is not exists
      Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308"));

      // Write new file
      try (final LfsWriter writer = storage.getWriter(null)) {
        writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      }

      // Write new file
      try (final LfsWriter writer = storage.getWriter(null)) {
        writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
      }
    } finally {
      TestHelper.deleteDirectory(tempDir);
    }
  }
}
