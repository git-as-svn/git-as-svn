/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf.internal;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.protobuf.ProtobufFormat;
import ru.bozaro.protobuf.RpcControllerFake;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Method information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class MethodInfo {
  @NotNull
  private final ProtobufFormat format;
  @NotNull
  private final Service service;
  @NotNull
  private final Descriptors.MethodDescriptor method;
  @NotNull
  private final Map<String, SetterInfo> fields;

  public MethodInfo(@NotNull Service service, @NotNull Descriptors.MethodDescriptor method, @NotNull ProtobufFormat format) {
    this.service = service;
    this.method = method;
    this.format = format;

    final Map<String, SetterInfo> fields = new HashMap<>();
    for (Descriptors.FieldDescriptor field : method.getInputType().getFields()) {
      final FieldParser parser = createFieldSetter(field);
      if (parser != null) {
        fields.put(field.getName(), new SetterInfo(field, parser));
      }
    }
    this.fields = fields;
  }

  @NotNull
  public String getName() {
    return method.getFullName();
  }

  @NotNull
  public CompletableFuture<Message> call(@NotNull Message request) {
    CompletableFuture<Message> future = new CompletableFuture<>();
    service.callMethod(method, RpcControllerFake.instance, request, response -> {
      try {
        if (response != null) {
          future.complete(response);
        } else {
          future.complete(null);
        }
      } catch (Throwable e) {
        future.completeExceptionally(e);
        throw e;
      }
    });
    return future;
  }

  @NotNull
  public CompletableFuture<byte[]> call(@NotNull Message request, @NotNull Charset charset) {
    return call(request).thenCompose(message -> {
      if (message == null) {
        return CompletableFuture.completedFuture(null);
      }
      try {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        responseToStream(message, stream, charset);
        return CompletableFuture.completedFuture(stream.toByteArray());
      } catch (IOException e) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        future.completeExceptionally(e);
        return future;
      }
    });
  }

  @NotNull
  public Message.Builder requestBuilder() {
    return service.getRequestPrototype(method).toBuilder();
  }

  public void requestByParams(@NotNull Message.Builder builder, @NotNull Map<String, String[]> params) throws ParseException {
    for (Map.Entry<String, String[]> entry : params.entrySet()) {
      final SetterInfo fieldParser = fields.get(entry.getKey());
      if (fieldParser != null) {
        String[] values = entry.getValue();
        for (int i = 0; i < values.length; ++i) {
          if (i == 0) {
            builder.setField(fieldParser.getField(), fieldParser.getParser().parse(values[i]));
          } else {
            builder.addRepeatedField(fieldParser.getField(), fieldParser.getParser().parse(values[i]));
          }
        }
      }
    }
  }

  public void requestByStream(@NotNull Message.Builder builder, @NotNull InputStream stream, @NotNull Charset charset) throws IOException {
    format.read(builder, stream, charset);
  }

  public void responseToStream(@NotNull Message message, @NotNull OutputStream stream, @NotNull Charset charset) throws IOException {
    format.write(message, stream, charset);
  }

  @Nullable
  private static FieldParser createFieldSetter(@NotNull Descriptors.FieldDescriptor field) {
    //noinspection SwitchStatementWithTooManyBranches
    switch (field.getType()) {
      case BOOL:
        return MethodInfo::parseBoolean;
      case STRING:
        return MethodInfo::parseString;
      case FIXED32:
      case SFIXED32:
      case INT32:
      case SINT32:
        return MethodInfo::parseInt32;
      case FIXED64:
      case SFIXED64:
      case INT64:
      case SINT64:
        return MethodInfo::parseInt64;
      case FLOAT:
        return MethodInfo::parseFloat;
      case DOUBLE:
        return MethodInfo::parseDouble;
      case ENUM: {
        final Map<String, Descriptors.EnumValueDescriptor> values = new HashMap<>();
        final String expected;
        final StringBuilder builder = new StringBuilder();
        for (Descriptors.EnumValueDescriptor value : field.getEnumType().getValues()) {
          values.put(value.getName().toLowerCase(Locale.ENGLISH), value);
          values.put(Integer.toString(value.getNumber()), value);
          if (builder.length() > 0) builder.append(", ");
          builder.append(value.getName());
        }
        expected = builder.toString();
        return value -> {
          final Descriptors.EnumValueDescriptor descriptor = values.get(value.toLowerCase(Locale.ENGLISH));
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
    switch (value.toLowerCase(Locale.ENGLISH)) {
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

  @NotNull
  public ProtobufFormat getFormat() {
    return format;
  }
}
