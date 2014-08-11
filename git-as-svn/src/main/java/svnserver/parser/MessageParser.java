package svnserver.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.parser.token.ListBeginToken;
import svnserver.parser.token.NumberToken;
import svnserver.parser.token.TextToken;
import svnserver.parser.token.WordToken;

import java.io.IOException;
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
  private interface Parser<T> {
    @NotNull
    Object parse(@Nullable SvnServerParser tokenParser) throws IOException;
  }

  @NotNull
  private static final String[] emptyStrings = {};
  @NotNull
  private static final int[] emptyInts = {};
  @NotNull
  private static final Map<Class<?>, Parser> parsers;

  static {
    parsers = new HashMap<>();
    parsers.put(String.class, MessageParser::parseString);
    parsers.put(String[].class, MessageParser::parseStrings);
    parsers.put(int.class, MessageParser::parseInt);
    parsers.put(int[].class, MessageParser::parseInts);
    parsers.put(boolean.class, MessageParser::parseBool);
  }

  @NotNull
  public static <T> T parse(@NotNull Class<T> type, @Nullable SvnServerParser tokenParser) throws IOException {
    Parser typeParser = parsers.get(type);
    if (typeParser != null) {
      //noinspection unchecked
      return (T) typeParser.parse(tokenParser);
    }
    return parseObject(type, tokenParser);
  }

  private static <T> T parseObject(Class<T> type, @Nullable SvnServerParser tokenParser) throws IOException {
    if (tokenParser != null) {
      tokenParser.readToken(ListBeginToken.class);
    }
    final Constructor<?>[] ctors = type.getDeclaredConstructors();
    if (ctors.length != 1) {
      throw new IllegalStateException("Can't find parser ctor for object: " + type.getName());
    }
    final Constructor<?> ctor = ctors[0];
    final Parameter[] ctorParams = ctor.getParameters();
    Object[] params = new Object[ctorParams.length];
    int depth = getDepth(tokenParser);
    for (int i = 0; i < params.length; ++i) {
      params[i] = parse(ctorParams[i].getType(), getDepth(tokenParser) == depth ? tokenParser : null);
    }
    while ((tokenParser != null) && (getDepth(tokenParser) >= depth)) {
      tokenParser.readToken();
    }
    try {
      //noinspection unchecked
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
  private static String[] parseStrings(@Nullable SvnServerParser tokenParser) throws IOException {
    if (tokenParser == null) {
      return emptyStrings;
    }
    if (tokenParser.readItem(ListBeginToken.class) != null) {
      final List<String> result = new ArrayList<>();
      while (true) {
        final TextToken token = tokenParser.readItem(TextToken.class);
        if (token == null) break;
        result.add(token.getText());
      }
      return result.toArray(new String[result.size()]);
    }
    return emptyStrings;
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
