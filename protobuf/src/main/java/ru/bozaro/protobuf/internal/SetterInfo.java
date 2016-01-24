/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf.internal;

import com.google.protobuf.Descriptors;
import org.jetbrains.annotations.NotNull;

/**
 * Field setter.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SetterInfo {
  @NotNull
  private final Descriptors.FieldDescriptor field;
  @NotNull
  private final FieldParser parser;

  public SetterInfo(@NotNull Descriptors.FieldDescriptor field, @NotNull FieldParser parser) {
    this.field = field;
    this.parser = parser;
  }

  @NotNull
  public Descriptors.FieldDescriptor getField() {
    return field;
  }

  @NotNull
  public FieldParser getParser() {
    return parser;
  }
}
