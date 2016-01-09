/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fake RpcController implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class RpcControllerFake implements RpcController {
  @NotNull
  public static final RpcControllerFake instance = new RpcControllerFake();

  @Override
  public void reset() {
  }

  @Override
  public boolean failed() {
    return false;
  }

  @Nullable
  @Override
  public String errorText() {
    return null;
  }

  @Override
  public void startCancel() {
  }

  @Override
  public void setFailed(@Nullable String reason) {
  }

  @Override
  public boolean isCanceled() {
    return false;
  }

  @Override
  public void notifyOnCancel(@NotNull RpcCallback<Object> callback) {
  }
}
