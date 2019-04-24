/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Helper for create documentation links.
 *
 * @author a.navrotskiy
 */
public enum ReferenceLink {
  Properties("ch_props.html");

  @NotNull
  public static final String BASE_URL = "https://bozaro.github.io/git-as-svn/html/";
  @NotNull
  private final static List<Lang> s_languages = Arrays.asList(
      new Lang("en_US", "On English"),
      new Lang("ru_RU", "По-русски")
  );
  @NotNull
  private final String docbookPage;

  ReferenceLink(@NotNull String docbookPage) {
    this.docbookPage = docbookPage;
  }

  @NotNull
  public final String getLinks() {
    StringBuilder sb = new StringBuilder();
    for (Lang lang : getLanguages()) {
      sb.append(" * ")
          .append(lang.getItemPrefix())
          .append(": ")
          .append(getLink(lang))
          .append("\n");
    }
    return sb.toString();
  }

  /**
   * Get available documentation locales.
   *
   * @return Available documentation locales.
   */
  @NotNull
  public static List<Lang> getLanguages() {
    return s_languages;
  }

  @NotNull
  public final String getLink(@NotNull Lang lang) {
    return BASE_URL + lang.getName() + "/" + docbookPage;
  }

  public final static class Lang {
    @NotNull
    private final String name;
    @NotNull
    private final String description;

    private Lang(@NotNull String name, @NotNull String description) {
      this.name = name;
      this.description = description;
    }

    @NotNull
    public String getName() {
      return name;
    }

    private @NotNull String getItemPrefix() {
      return description;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
