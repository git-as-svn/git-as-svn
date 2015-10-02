/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import com.google.common.io.ByteStreams;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.internal.junit.ArrayAsserts;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * Test for TemporaryOutputStream.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class TemporaryOutputStreamTest {
  private static final int MAX_MEMORY_SIZE = 10240;

  @SuppressWarnings("MagicNumber")
  @NotNull
  @DataProvider
  public static Object[][] providerReadWrite() {
    return new Object[][]{
        new Object[]{0, 1000},
        new Object[]{100, 1000},
        new Object[]{0, MAX_MEMORY_SIZE},
        new Object[]{100, MAX_MEMORY_SIZE},
        new Object[]{0, MAX_MEMORY_SIZE + 1},
        new Object[]{100, MAX_MEMORY_SIZE + 1},
        new Object[]{0, MAX_MEMORY_SIZE * 3},
        new Object[]{100, MAX_MEMORY_SIZE * 3}
    };
  }

  @SuppressWarnings("OverlyLongMethod")
  @Test(dataProvider = "providerReadWrite")
  public void checkReadWrite(int blockSize, int totalSize) throws IOException {
    final ByteArrayOutputStream expectedStream = new ByteArrayOutputStream();
    try (final TemporaryOutputStream outputStream = new TemporaryOutputStream(MAX_MEMORY_SIZE)) {
      final Random random = new Random(0);
      int writeSize = 0;
      while (writeSize < totalSize) {
        if (blockSize == 0) {
          final byte data = (byte) random.nextInt();
          outputStream.write(data);
          expectedStream.write(data);
          writeSize++;
        } else {
          final byte[] data = new byte[blockSize];
          random.nextBytes(data);
          final int offset = random.nextInt(blockSize - 1);
          final int count = Math.min(random.nextInt(blockSize - offset - 1) + 1, totalSize - writeSize);
          outputStream.write(data, offset, count);
          expectedStream.write(data, offset, count);
          writeSize += count;
        }
      }
      Assert.assertEquals(outputStream.tempFile() == null, totalSize <= MAX_MEMORY_SIZE);
      Assert.assertEquals(expectedStream.size(), totalSize);
      Assert.assertEquals(outputStream.size(), totalSize);

      final ByteArrayOutputStream actualStream = new ByteArrayOutputStream();
      //noinspection NestedTryStatement
      try (final InputStream inputStream = outputStream.toInputStream()) {
        int readSize = 0;
        while (true) {
          Assert.assertTrue(readSize <= totalSize);
          if (blockSize == 0) {
            final int data = inputStream.read();
            if (data < 0) break;
            actualStream.write(data);
            readSize++;
          } else {
            final byte[] data = new byte[blockSize];
            final int offset = random.nextInt(blockSize - 1);
            final int count = random.nextInt(blockSize - offset - 1) + 1;
            final int size = inputStream.read(data, offset, count);
            Assert.assertTrue(size != 0);
            if (size < 0) {
              break;
            }
            actualStream.write(data, offset, size);
            readSize += size;
          }
        }
        Assert.assertEquals(readSize, totalSize);
      }
      Assert.assertEquals(actualStream.size(), totalSize);

      ArrayAsserts.assertArrayEquals(actualStream.toByteArray(), expectedStream.toByteArray());
    }
  }

  @Test
  public void checkLifeTime() throws IOException {
    final byte[] expectedData = new byte[MAX_MEMORY_SIZE * 2];
    final Random random = new Random(0);
    random.nextBytes(expectedData);

    final TemporaryOutputStream outputStream = new TemporaryOutputStream(MAX_MEMORY_SIZE);
    Assert.assertNull(outputStream.tempFile());
    outputStream.write(expectedData);
    checkFileExists(outputStream, true);

    final InputStream inputStream1 = outputStream.toInputStream();
    final InputStream inputStream2 = outputStream.toInputStream();

    final byte[] actualData1 = ByteStreams.toByteArray(inputStream1);
    inputStream1.close();
    inputStream1.close();

    outputStream.close();
    outputStream.close();

    final byte[] actualData2 = ByteStreams.toByteArray(inputStream2);
    checkFileExists(outputStream, true);
    inputStream2.close();
    checkFileExists(outputStream, false);
    inputStream2.close();
    checkFileExists(outputStream, false);

    ArrayAsserts.assertArrayEquals(actualData1, expectedData);
    ArrayAsserts.assertArrayEquals(actualData2, expectedData);
  }

  private static void checkFileExists(@NotNull TemporaryOutputStream outputStream, boolean exists) {
    final File tempFile = outputStream.tempFile();
    Assert.assertNotNull(tempFile);
    Assert.assertEquals(tempFile.exists(), exists);
  }
}
