/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

import java.util.Locale;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public enum Depth {
  Empty {
    @NotNull
    @Override
    public Action determineAction(@NotNull Depth requestedDepth, boolean directory) {
      if (requestedDepth == Immediates || requestedDepth == Infinity)
        return Action.Upgrade;

      return Action.Skip;
    }

    @NotNull
    public <R> R visit(@NotNull DepthVisitor<R> visitor) throws SVNException {
      return visitor.visitEmpty();
    }
  },

  Files {
    @NotNull
    @Override
    public Action determineAction(@NotNull Depth requestedDepth, boolean directory) {
      if (directory)
        return requestedDepth == Immediates || requestedDepth == Infinity ? Action.Upgrade : Action.Skip;

      return requestedDepth == Empty ? Action.Skip : Action.Normal;
    }

    @NotNull
    public <R> R visit(@NotNull DepthVisitor<R> visitor) throws SVNException {
      return visitor.visitFiles();
    }
  },

  Immediates {
    @NotNull
    @Override
    public Action determineAction(@NotNull Depth requestedDepth, boolean directory) {
      if (directory)
        return requestedDepth == Empty || requestedDepth == Files ? Action.Skip : Action.Normal;

      return requestedDepth == Empty ? Action.Skip : Action.Normal;
    }

    @NotNull
    public <R> R visit(@NotNull DepthVisitor<R> visitor) throws SVNException {
      return visitor.visitImmediates();
    }
  },

  Infinity {
    @NotNull
    @Override
    public Action determineAction(@NotNull Depth requestedDepth, boolean directory) {
      if (directory)
        return requestedDepth == Empty || requestedDepth == Files ? Action.Skip : Action.Normal;

      return requestedDepth == Empty ? Action.Skip : Action.Normal;
    }

    @NotNull
    public <R> R visit(@NotNull DepthVisitor<R> visitor) throws SVNException {
      return visitor.visitInfinity();
    }
  };

  @NotNull
  private final String value = name().toLowerCase(Locale.ENGLISH);

  @NotNull
  public static Depth parse(@NotNull String value) {
    for (Depth depth : values())
      if (depth.value.equals(value))
        return depth;

    return Infinity;
  }

  @NotNull
  public static Depth parse(@NotNull String value, boolean recurse, @NotNull Depth nonRecurse) {
    if (value.isEmpty())
      return recurse ? Infinity : nonRecurse;

    return parse(value);
  }

  @NotNull
  public abstract <R> R visit(@NotNull DepthVisitor<R> visitor) throws SVNException;

  @NotNull
  public abstract Action determineAction(@NotNull Depth requestedDepth, boolean directory);

  public enum Action {
    Skip,
    Upgrade,
    Normal
  }

  @NotNull
  public final Depth deepen() {
    return this == Immediates ? Empty : this;
  }
}
