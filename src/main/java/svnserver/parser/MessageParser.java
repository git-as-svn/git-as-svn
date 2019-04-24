/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.parser.token.*;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parse data from class.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class MessageParser {
  private interface Parser {
    @NotNull
    Object parse(@Nullable SvnServerParser tokenParser) throws IOException;
  }

  @NotNull
  private static final byte[] emptyBytes = {};
  @NotNull
  private static final int[] emptyInts = {};
  @NotNull
  private static final Map<Class<?>, Parser> parsers;

  static {
    parsers = new HashMap<>();
    parsers.put(String.class, MessageParser::parseString);
    parsers.put(byte[].class, MessageParser::parseBinary);
    parsers.put(int.class, MessageParser::parseInt);
    parsers.put(int[].class, MessageParser::parseInts);
    parsers.put(boolean.class, MessageParser::parseBool);
  }

  @SuppressWarnings("unchecked")
  @NotNull
  public static <T> T parse(@NotNull Class<T> type, @Nullable SvnServerParser tokenParser) throws IOException {
    Parser typeParser = parsers.get(type);
    if (typeParser != null) {
      return (T) typeParser.parse(tokenParser);
    }
    return parseObject(type, tokenParser);
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private static <T> T parseObject(@NotNull Class<T> type, @Nullable SvnServerParser tokenParser) throws IOException {
    if (tokenParser != null && tokenParser.readItem(ListBeginToken.class) == null)
      tokenParser = null;

    final int depth = getDepth(tokenParser);

    if (type.isArray()) {
      final List<Object> result = new ArrayList<>();

      if (tokenParser != null) {
        while (true) {
          final Object element = parse(type.getComponentType(), tokenParser);
          if (getDepth(tokenParser) < depth)
            break;

          result.add(element);
        }
      }

      return (T) result.toArray((Object[]) Array.newInstance(type.getComponentType(), result.size()));
    }

    final Constructor<?>[] ctors = type.getDeclaredConstructors();
    if (ctors.length != 1) {
      throw new IllegalStateException("Can't find parser ctor for object: " + type.getName());
    }
    final Constructor<?> ctor = ctors[0];
    final Parameter[] ctorParams = ctor.getParameters();
    Object[] params = new Object[ctorParams.length];
    for (int i = 0; i < params.length; ++i) {
      params[i] = parse(ctorParams[i].getType(), getDepth(tokenParser) == depth ? tokenParser : null);
    }
    while (tokenParser != null && getDepth(tokenParser) >= depth) {
        tokenParser.readToken();
    }

    try {
      if (!ctor.isAccessible())
        ctor.setAccessible(true);

      return (T) ctor.newInstance(params);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @NotNull
  private static String parseString(@Nullable SvnServerParser tokenParser) throws IOException {
    if (tokenParser == null) {
      return "";
    }
    final TextToken token = tokenParser.readItem(TextToken.class);
    return token != null ? token.getText() : "";
  }

  @NotNull
  private static byte[] parseBinary(@Nullable SvnServerParser tokenParser) throws IOException {
    if (tokenParser == null) {
      return emptyBytes;
    }
    final StringToken token = tokenParser.readItem(StringToken.class);
    return token != null ? token.getData() : emptyBytes;
  }

  private static int parseInt(@Nullable SvnServerParser tokenParser) throws IOException {
    if (tokenParser == null) {
      return 0;
    }
    final NumberToken token = tokenParser.readItem(NumberToken.class);
    return token != null ? token.getNumber() : 0;
  }

  private static boolean parseBool(@Nullable SvnServerParser tokenParser) throws IOException {
    if (tokenParser == null) {
      return false;
    }
    final WordToken token = tokenParser.readItem(WordToken.class);
    return token != null && token.getText().equals("true");
  }

  @NotNull
  private static int[] parseInts(@Nullable SvnServerParser tokenParser) throws IOException {
    if (tokenParser == null) {
      return emptyInts;
    }
    if (tokenParser.readItem(ListBeginToken.class) != null) {
      final List<Integer> result = new ArrayList<>();
      while (true) {
        final NumberToken token = tokenParser.readItem(NumberToken.class);
        if (token == null) break;
        result.add(token.getNumber());
      }
      final int[] array = new int[result.size()];
      for (int i = 0; i < array.length; ++i) {
        array[i] = result.get(i);
      }
      return array;
    }
    return emptyInts;
  }

  private static int getDepth(@Nullable SvnServerParser tokenParser) {
    return tokenParser == null ? -1 : tokenParser.getDepth();
  }
}
