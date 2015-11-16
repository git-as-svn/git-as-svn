/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.api;

import com.google.protobuf.BlockingService;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.Service;
import org.atteo.classindex.ClassIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

/**
 * Servlet wrapper for Protobuf RPC
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ProtobufRpcServlet extends HttpServlet {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(ProtobufRpcServlet.class);

  public static class SetterMeta {
    @NotNull
    private final Descriptors.FieldDescriptor field;
    @NotNull
    private final Parser parser;

    public SetterMeta(@NotNull Descriptors.FieldDescriptor field, @NotNull Parser parser) {
      this.field = field;
      this.parser = parser;
    }
  }

  @FunctionalInterface
  public interface Parser<T> {
    T parse(@NotNull String value) throws ParseException;
  }

  @NotNull
  private final transient ConcurrentHashMap<String, ServiceInfo> services = new ConcurrentHashMap<>();
  @NotNull
  private static final ProtobufFormat[] formats = collectFormats();

  private static ProtobufFormat[] collectFormats() {
    return StreamSupport
        .stream(ClassIndex.getSubclasses(ProtobufFormat.class).spliterator(), false)
        .filter(type -> !Modifier.isAbstract(type.getModifiers()))
        .map(type -> {
          try {
            return type.newInstance();
          } catch (IllegalAccessException | InstantiationException e) {
            throw new IllegalStateException(e);
          }
        })
        .toArray(ProtobufFormat[]::new);
  }

  public final class Holder {
    @NotNull
    private final ServiceInfo service;

    private Holder(@NotNull ServiceInfo service) {
      this.service = service;
    }

    public void removeService() {
      ProtobufRpcServlet.this.removeService(this);
    }
  }

  private static class ServiceInfo {
    @NotNull
    private final Service service;
    @NotNull
    private final Map<String, MethodInfo> methods;
    @NotNull
    private final String name;

    public ServiceInfo(@NotNull Service service) {
      this.service = service;
      this.name = service.getDescriptorForType().getName().toLowerCase();

      final Map<String, MethodInfo> methods = new HashMap<>();
      for (Descriptors.MethodDescriptor method : service.getDescriptorForType().getMethods()) {
        for (ProtobufFormat format : formats) {
          methods.put(method.getName().toLowerCase() + format.getSuffix(), new MethodInfo(format, method, collectFields(method.getInputType().getFields())));
        }
      }
      this.methods = methods;
    }

    @NotNull
    public String getName() {
      return name;
    }

    @Nullable
    private MethodInfo getMethod(@NotNull String path) {
      return methods.get(path);
    }
  }

  private static class MethodInfo {
    @NotNull
    private final ProtobufFormat format;
    @NotNull
    private final Descriptors.MethodDescriptor method;
    @NotNull
    private final Map<String, SetterMeta> fields;

    private MethodInfo(@NotNull ProtobufFormat format, @NotNull Descriptors.MethodDescriptor method, @NotNull Map<String, SetterMeta> fields) {
      this.format = format;
      this.method = method;
      this.fields = fields;
    }
  }

  @NotNull
  public Holder addService(@NotNull final BlockingService service) {
    return addService(new BlockingServiceWrapper(service));
  }

  @NotNull
  public Holder addService(@NotNull final Service service) {
    final ServiceInfo serviceInfo = new ServiceInfo(service);
    services.put(serviceInfo.getName(), serviceInfo);
    return new Holder(serviceInfo);
  }

  public boolean removeService(@NotNull final Holder holder) {
    return services.remove(holder.service.getName(), holder);
  }

  @NotNull
  private static Map<String, SetterMeta> collectFields(@NotNull List<Descriptors.FieldDescriptor> fields) {
    final Map<String, SetterMeta> result = new HashMap<>();
    for (Descriptors.FieldDescriptor field : fields) {
      final Parser parser = createFieldSetter(field);
      if (parser != null) {
        result.put(field.getName(), new SetterMeta(field, parser));
      }
    }
    return result;
  }

  @Nullable
  private static Parser createFieldSetter(@NotNull Descriptors.FieldDescriptor field) {
    //noinspection SwitchStatementWithTooManyBranches
    switch (field.getType()) {
      case BOOL:
        return ProtobufRpcServlet::parseBoolean;
      case STRING:
        return ProtobufRpcServlet::parseString;
      case FIXED32:
      case SFIXED32:
      case INT32:
      case SINT32:
        return ProtobufRpcServlet::parseInt32;
      case FIXED64:
      case SFIXED64:
      case INT64:
      case SINT64:
        return ProtobufRpcServlet::parseInt64;
      case FLOAT:
        return ProtobufRpcServlet::parseFloat;
      case DOUBLE:
        return ProtobufRpcServlet::parseDouble;
      case ENUM: {
        final Map<String, Descriptors.EnumValueDescriptor> values = new HashMap<>();
        final String expected;
        final StringBuilder builder = new StringBuilder();
        for (Descriptors.EnumValueDescriptor value : field.getEnumType().getValues()) {
          values.put(value.getName().toLowerCase(), value);
          if (builder.length() > 0) builder.append(", ");
          builder.append(value.getName());
        }
        expected = builder.toString();
        return value -> {
          final Descriptors.EnumValueDescriptor descriptor = values.get(value.toLowerCase());
          if (descriptor == null) {
            throw new ParseException("Can't parse enum value: [" + value + "] for field [" + field.getFullName() + "] expected one of [" + expected + ']', 0);
          }
          return descriptor;
        };
      }
      default:
        return null;
    }
  }

  @NotNull
  private static Boolean parseBoolean(@NotNull String value) throws ParseException {
    switch (value.toLowerCase()) {
      case "true":
      case "yes":
      case "on":
        return Boolean.TRUE;
      case "false":
      case "no":
      case "off":
        return Boolean.FALSE;
      default:
        throw new ParseException("Can't parse boolean value: [" + value + "]", 0);
    }
  }

  @NotNull
  private static Integer parseInt32(@NotNull String value) throws ParseException {
    return Integer.valueOf(value, 10);
  }

  @NotNull
  private static Long parseInt64(@NotNull String value) throws ParseException {
    if (value.indexOf('T') > 0) {
      @NotNull
      final SimpleDateFormat utcDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
      return utcDateFormatter.parse(value).getTime();
    } else {
      return Long.valueOf(value, 10);
    }
  }

  @NotNull
  private static Float parseFloat(@NotNull String value) throws ParseException {
    return Float.valueOf(value);
  }

  @NotNull
  private static Double parseDouble(@NotNull String value) throws ParseException {
    return Double.valueOf(value);
  }

  @NotNull
  private static String parseString(@NotNull String value) {
    return value;
  }

  @Override
  protected void service(@NotNull HttpServletRequest req, @NotNull final HttpServletResponse res) throws ServletException, IOException {
    final String pathInfo = req.getPathInfo();
    if (pathInfo != null) {
      final int begin = pathInfo.charAt(0) == '/' ? 1 : 0;
      final int separator = pathInfo.indexOf('/', begin);
      if (separator > 0) {
        ServiceInfo serviceInfo = services.get(pathInfo.substring(begin, separator));
        if (serviceInfo != null) {
          service(req, res, pathInfo.substring(separator + 1), serviceInfo);
          return;
        }
      }
    }
    res.sendError(HttpServletResponse.SC_NOT_FOUND, "Service not found: " + pathInfo);
  }

  private void service(@NotNull HttpServletRequest req, @NotNull final HttpServletResponse res, @NotNull final String methodPath, @NotNull ServiceInfo serviceInfo) throws ServletException, IOException {
    final MethodInfo method = serviceInfo.getMethod(methodPath);
    if (method == null) {
      res.sendError(HttpServletResponse.SC_NOT_FOUND, "Method not found: " + methodPath);
      return;
    }
    final Message msgRequestType = serviceInfo.service.getRequestPrototype(method.method);
    final Message msgRequest;
    final Message.Builder builder = msgRequestType.toBuilder();
    if ("GET".equals(req.getMethod())) {
      msgRequest = readGetParams(builder, method, req).build();
    } else {
      msgRequest = method.format.read(builder, req);
      if (msgRequest == null) {
        res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Method serialization reader is not supported.");
        return;
      }
    }
    serviceInfo.service.callMethod(method.method, RpcControllerFake.instance, msgRequest, msgResponce -> {
      try {
        if (msgResponce != null) {
          writeResult(method.format, res, msgResponce);
        } else {
          res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
      } catch (IOException e) {
        log.error("Method error " + method.method.getFullName(), e);
      }
    });
  }

  @NotNull
  private static Message.Builder readGetParams(@NotNull Message.Builder builder, @NotNull MethodInfo method, @NotNull HttpServletRequest req) throws ServletException {
    try {
      @SuppressWarnings("rawtypes")
      final Enumeration iter = req.getParameterNames();
      while (iter.hasMoreElements()) {
        final String paramName = iter.nextElement().toString();
        final SetterMeta fieldParser = method.fields.get(paramName);
        if (fieldParser != null) {
          builder.setField(fieldParser.field, fieldParser.parser.parse(req.getParameter(paramName)));
        }
      }
      return builder;
    } catch (ParseException e) {
      throw new ServletException(e);
    }
  }

  protected void writeResult(@NotNull ProtobufFormat format, @NotNull HttpServletResponse res, @NotNull Message message) throws IOException {
    res.setContentType(format.getMimeType());
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    format.write(message, res);
  }

}
