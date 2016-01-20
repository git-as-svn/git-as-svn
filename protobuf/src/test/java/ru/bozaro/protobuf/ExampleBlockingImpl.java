/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import ru.bozaro.protobuf.example.EchoMessage;
import ru.bozaro.protobuf.example.Example;
import ru.bozaro.protobuf.example.HelloRequest;
import ru.bozaro.protobuf.example.HelloResponse;

/**
 * Simple example.proto service implementation.
 *
 * @author Artem V. Navrotskiy
 */
public class ExampleBlockingImpl implements Example.BlockingInterface {
  @Override
  public HelloResponse hello(RpcController controller, HelloRequest request) throws ServiceException {
    final StringBuilder greeting = new StringBuilder("Hello, ");
    if (request.hasTitle()) {
      greeting.append(request.getTitle()).append(" ");
    }
    greeting.append(request.getPerson());
    return HelloResponse.newBuilder()
        .setGreeting(greeting.toString())
        .build();
  }

  @Override
  public EchoMessage echo(RpcController controller, EchoMessage request) throws ServiceException {
    return request;
  }
}
