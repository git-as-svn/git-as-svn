/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.memory;

import org.apache.commons.io.IOUtils;
import org.testng.Assert;
import org.testng.annotations.Test;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsWriter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Simple test for LfsMemoryStorage.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsMemoryStorageTest {
  @Test
  public void simple() throws IOException {
    LfsMemoryStorage storage = new LfsMemoryStorage();
    // Check file is not exists
    Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308"));

    // Write new file
    try (final LfsWriter writer = storage.getWriter()) {
      writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
      Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
    }

    // Read old file.
    final LfsReader reader = storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
    Assert.assertNotNull(reader);
    Assert.assertEquals("9fe77772b085e3533101d59d33a51f19", reader.getMd5());
    Assert.assertEquals(15, reader.getSize());

    try (final InputStream stream = reader.openStream()) {
      Assert.assertEquals(IOUtils.toString(stream, StandardCharsets.UTF_8), "Hello, world!!!");
    }
  }

  @Test
  public void alreadyAdded() throws IOException {
    LfsMemoryStorage storage = new LfsMemoryStorage();
    // Check file is not exists
    Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308"));

    // Write new file
    try (final LfsWriter writer = storage.getWriter()) {
      writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
      Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
    }

    // Write new file
    try (final LfsWriter writer = storage.getWriter()) {
      writer.write("Hello, world!!!".getBytes(StandardCharsets.UTF_8));
      Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308");
    }
  }
}
