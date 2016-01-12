package ru.bozaro.protobuf;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.Test;
import ru.bozaro.protobuf.client.ProtobufClient;
import ru.bozaro.protobuf.example.EchoMessage;
import ru.bozaro.protobuf.example.Example;
import ru.bozaro.protobuf.internal.ServiceInfo;

/**
 * Simple test for ProtobufRpcServlet.
 *
 * @author Artem V. Navrotskiy
 */
public class ProtobufRpcServletTest {
  @Test(enabled = false)
  public void methodPost() throws Exception {
    try (final EmbeddedHttpServer server = new EmbeddedHttpServer()) {
      server.addServlet("/api/*", new ProtobufRpcServlet(new ServiceHolder() {
        @Nullable
        @Override
        public ServiceInfo getService(@NotNull String name) {
          return null;
        }
      }));

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
