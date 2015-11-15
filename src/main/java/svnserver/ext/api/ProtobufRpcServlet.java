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
  private final transient Map<String, MethodInfo> methods;
  @NotNull
  private final transient Service service;
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

  public ProtobufRpcServlet(@NotNull final BlockingService service) {
    this(new BlockingServiceWrapper(service));
  }

  public ProtobufRpcServlet(@NotNull Service service) {
    this.service = service;
    this.methods = collectMethods(service);
  }

  @NotNull
  private static Map<String, MethodInfo> collectMethods(@NotNull Service service) {
    final Map<String, MethodInfo> methods = new HashMap<>();
    for (Descriptors.MethodDescriptor method : service.getDescriptorForType().getMethods()) {
      for (ProtobufFormat format : formats) {
        methods.put('/' + method.getName() + format.getSuffix(), new MethodInfo(format, method, collectFields(method.getInputType().getFields())));
      }
    }
    return methods;
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
    final String methodName = req.getPathInfo();
    if ((methodName == null) || (methodName.equals("/"))) {
      writeResult(formats[0], res, service.getDescriptorForType().getFile().toProto());
      return;
    }
    final MethodInfo method = methods.get(methodName);
    if (method == null) {
      res.sendError(HttpServletResponse.SC_NOT_FOUND, "Method not found: " + methodName);
      return;
    }
    final Message msgRequestType = service.getRequestPrototype(method.method);
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
    service.callMethod(method.method, RpcControllerFake.instance, msgRequest, msgResponce -> {
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
