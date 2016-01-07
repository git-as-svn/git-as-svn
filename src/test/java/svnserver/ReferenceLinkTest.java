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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test for ReferenceHelper.
 *
 * @author a.navrotskiy
 */
public class ReferenceLinkTest {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(ReferenceLinkTest.class);

  @NotNull
  @DataProvider
  public static Object[][] referenceLinkData() {
    final List<Object[]> result = new ArrayList<>();
    for (ReferenceLink referenceLink : ReferenceLink.values()) {
      for (ReferenceLink.Lang lang : ReferenceLink.getLanguages()) {
        result.add(new Object[]{referenceLink, lang});
      }
    }
    return result.toArray(new Object[result.size()][]);
  }

  @Test
  public void languagesList() {
    File poDir = new File("docbook/src/main/po");
    log.info("Localization path: " + poDir.getAbsolutePath());
    Assert.assertTrue(poDir.isDirectory());

    final Set<String> expected = Arrays.asList(poDir.listFiles(pathname -> pathname.isFile() && pathname.getName().endsWith(".po")))
        .stream()
        .map((file) -> {
          final String name = file.getName();
          return name.substring(0, name.length() - 3);
        })
        .collect(Collectors.toSet());

    final Set<String> actual = ReferenceLink.getLanguages()
        .stream()
        .map(ReferenceLink.Lang::getName)
        .collect(Collectors.toSet());
    Assert.assertEquals(actual, expected);
  }

  @Test(dataProvider = "referenceLinkData")
  public void referenceLink(@NotNull ReferenceLink referenceLink, @NotNull ReferenceLink.Lang lang) throws IOException {
    try (InputStream stream = new URL(referenceLink.getLink(lang)).openStream()) {
      ByteStreams.toByteArray(stream);
    }
  }
}
