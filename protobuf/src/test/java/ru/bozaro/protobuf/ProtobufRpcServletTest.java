/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.BlockingService;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import org.testng.Assert;
import org.testng.annotations.Test;
import ru.bozaro.protobuf.client.ProtobufClient;
import ru.bozaro.protobuf.example.EchoMessage;
import ru.bozaro.protobuf.example.Example;
import ru.bozaro.protobuf.example.HelloRequest;
import ru.bozaro.protobuf.example.HelloResponse;
import ru.bozaro.protobuf.internal.ServiceInfo;

import java.util.Map;

/**
 * Simple test for ProtobufRpcServlet.
 *
 * @author Artem V. Navrotskiy
 */
public class ProtobufRpcServletTest {
  @Test
  public void methodPost() throws Exception {
    BlockingService service = Example.newReflectiveBlockingService(new Example.BlockingInterface() {
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
    });

    final Map<String, ServiceInfo> services = ImmutableMap.<String, ServiceInfo>builder()
        .put(service.getDescriptorForType().getName().toLowerCase(), new ServiceInfo(new BlockingServiceWrapper(service)))
        .build();

    try (final EmbeddedHttpServer server = new EmbeddedHttpServer()) {
      server.addServlet("/api/*", new ProtobufRpcServlet(services::get));

      ProtobufClient channel = new ProtobufClient(server.getBase().resolve("/api"), null, null);
      Example.BlockingInterface stub = Example.newBlockingStub(channel);
      // Check echo method.
      final EchoMessage echoRequest = EchoMessage.newBuilder()
          .setText("Foo")
          .build();
      final EchoMessage echoResponse = stub.echo(null, echoRequest);
      Assert.assertEquals(echoRequest, echoResponse);
    }
  }
}
