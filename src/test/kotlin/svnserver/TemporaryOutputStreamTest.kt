/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver

import org.apache.commons.io.IOUtils
import org.testng.Assert
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import org.testng.internal.junit.ArrayAsserts
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.*
import kotlin.math.min

/**
 * Test for TemporaryOutputStream.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class TemporaryOutputStreamTest {
    @Test(dataProvider = "providerReadWrite")
    fun checkReadWrite(blockSize: Int, totalSize: Int) {
        val expectedStream = ByteArrayOutputStream()
        TemporaryOutputStream(MAX_MEMORY_SIZE).use { outputStream ->
            val random = Random(0)
            var writeSize = 0
            while (writeSize < totalSize) {
                if (blockSize == 0) {
                    val data = random.nextInt().toByte()
                    outputStream.write(data.toInt())
                    expectedStream.write(data.toInt())
                    writeSize++
                } else {
                    val data = ByteArray(blockSize)
                    random.nextBytes(data)
                    val offset = random.nextInt(blockSize - 1)
                    val count = min(random.nextInt(blockSize - offset - 1) + 1, totalSize - writeSize)
                    outputStream.write(data, offset, count)
                    expectedStream.write(data, offset, count)
                    writeSize += count
                }
            }
            Assert.assertEquals(outputStream.tempFile() == null, totalSize <= MAX_MEMORY_SIZE)
            Assert.assertEquals(expectedStream.size(), totalSize)
            Assert.assertEquals(outputStream.size(), totalSize.toLong())
            val actualStream = ByteArrayOutputStream()
            outputStream.toInputStream().use { inputStream ->
                var readSize = 0
                while (true) {
                    Assert.assertTrue(readSize <= totalSize)
                    if (blockSize == 0) {
                        val data = inputStream.read()
                        if (data < 0) break
                        actualStream.write(data)
                        readSize++
                    } else {
                        val data = ByteArray(blockSize)
                        val offset = random.nextInt(blockSize - 1)
                        val count = random.nextInt(blockSize - offset - 1) + 1
                        val size = inputStream.read(data, offset, count)
                        Assert.assertTrue(size != 0)
                        if (size < 0) {
                            break
                        }
                        actualStream.write(data, offset, size)
                        readSize += size
                    }
                }
                Assert.assertEquals(readSize, totalSize)
            }
            Assert.assertEquals(actualStream.size(), totalSize)
            ArrayAsserts.assertArrayEquals(actualStream.toByteArray(), expectedStream.toByteArray())
        }
    }

    @Test
    fun checkLifeTime() {
        val expectedData = ByteArray(MAX_MEMORY_SIZE * 2)
        val random = Random(0)
        random.nextBytes(expectedData)
        val outputStream = TemporaryOutputStream(MAX_MEMORY_SIZE)
        Assert.assertNull(outputStream.tempFile())
        outputStream.write(expectedData)
        val tempFile = outputStream.tempFile()!!
        Assert.assertTrue(Files.exists(tempFile))
        val inputStream = outputStream.toInputStream()
        Assert.assertTrue(Files.exists(tempFile))
        val actualData = IOUtils.toByteArray(inputStream)
        Assert.assertTrue(Files.exists(tempFile))
        inputStream.close()
        Assert.assertFalse(Files.exists(tempFile))
        inputStream.close()
        Assert.assertFalse(Files.exists(tempFile))
        outputStream.close()
        Assert.assertFalse(Files.exists(tempFile))
        outputStream.close()
        Assert.assertFalse(Files.exists(tempFile))
        ArrayAsserts.assertArrayEquals(actualData, expectedData)
    }

    companion object {
        private const val MAX_MEMORY_SIZE = 10240

        @JvmStatic
        @DataProvider
        fun providerReadWrite(): Array<Array<Any>> {
            return arrayOf(arrayOf(0, 1000), arrayOf(100, 1000), arrayOf(0, MAX_MEMORY_SIZE), arrayOf(100, MAX_MEMORY_SIZE), arrayOf(0, MAX_MEMORY_SIZE + 1), arrayOf(100, MAX_MEMORY_SIZE + 1), arrayOf(0, MAX_MEMORY_SIZE * 3), arrayOf(100, MAX_MEMORY_SIZE * 3))
        }
    }
}
