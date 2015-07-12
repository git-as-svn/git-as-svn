/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.filter;

import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Tests for LfsPointer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsPointerTest {
  @Test
  public void parsePointerTest() {
    final Map<String, String> pointer = LfsPointer.parsePointer(("version https://git-lfs.github.com/spec/v1\n" +
        "hash 12345\n" +
        "oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n" +
        "size 12345\n").getBytes(StandardCharsets.UTF_8));
    Assert.assertNotNull(pointer);
    Assert.assertEquals(pointer.get("version"), "https://git-lfs.github.com/spec/v1");
    Assert.assertEquals(pointer.get("oid"), "sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393");
    Assert.assertEquals(pointer.get("size"), "12345");
    Assert.assertEquals(pointer.get("hash"), "12345");
    Assert.assertEquals(pointer.size(), 4);
  }

  @DataProvider
  public static Object[][] parseNonPointerData() {
    return new Object[][]{
        new Object[]{
            "No final line ending",
            "version https://git-lfs.github.com/spec/v1\n" +
                "oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n" +
                "size 12345"
        },
        new Object[]{
            "Unexpected final line ending",
            "version https://git-lfs.github.com/spec/v1\n" +
                "oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n" +
                "size 12345\n\n"
        },
        new Object[]{
            "Not found require field",
            "version https://git-lfs.github.com/spec/v1\n" +
                "hash sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n" +
                "size 12345\n"
        },
        new Object[]{
            "Invalid key value",
            "version https://git-lfs.github.com/spec/v1\n" +
                "oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n" +
                "oid:data simple\n" +
                "size 12345\n\n"
        },
        new Object[]{
            "Invalid element order",
            "version https://git-lfs.github.com/spec/v1\n" +
                "oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n" +
                "hash 12345\n" +
                "size 12345\n"
        },
        new Object[]{
            "Invalid element order",
            "oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n" +
                "size 12345\n" +
                "version https://git-lfs.github.com/spec/v1\n"
        },
        new Object[]{
            "Double elements",
            "version https://git-lfs.github.com/spec/v1\n" +
                "hash 12345\n" +
                "hash 12345\n" +
                "oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n" +
                "size 12345\n"
        },
        new Object[]{
            "Double version",
            "version https://git-lfs.github.com/spec/v1\n" +
                "oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n" +
                "size 12345\n" +
                "version https://git-lfs.github.com/spec/v1\n"
        },
    };
  }

  @Test(dataProvider = "parseNonPointerData")
  public void parseNonPointerTest(@NotNull String message, @NotNull String pointer) {
    Assert.assertNull(LfsPointer.parsePointer(pointer.getBytes(StandardCharsets.UTF_8)), message);
  }

  @Test
  public void parseAndSerialize() {
    final Map<String, String> pointer = LfsPointer.createPointer("sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393", 12345);
    Assert.assertEquals(pointer.get("oid"), "sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393");
    Assert.assertEquals(pointer.get("size"), "12345");
    Assert.assertEquals(pointer.size(), 3);
    final byte[] bytes = LfsPointer.serializePointer(pointer);
    final Map<String, String> parsed = LfsPointer.parsePointer(bytes);
    Assert.assertEquals(parsed, pointer);
  }

  @Test
  public void parsePointerNonUtfTest() {
    final byte[] pointer = ("version https://git-lfs.github.com/spec/v1\n" +
        "oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n" +
        "size 12345\n").getBytes(StandardCharsets.UTF_8);
    pointer[100] = (byte) 0xFF;
    pointer[101] = (byte) 0xFF;
    Assert.assertNull(LfsPointer.parsePointer(pointer));
  }
}
