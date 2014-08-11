package svnserver.parser;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.internal.junit.ArrayAsserts;
import svnserver.parser.token.*;
import svnserver.server.msg.AuthInfoReq;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Тесты для проверки парсера.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnServerParserTest {
  @SuppressWarnings("MagicNumber")
  @Test
  public void testSimpleParse() throws IOException {
    try (InputStream stream = new ByteArrayInputStream("( word 22 10:string 1:x 1:  8:Тест ( sublist ) ) ".getBytes(StandardCharsets.UTF_8))) {
      final SvnServerParser parser = new SvnServerParser(stream);
      Assert.assertEquals(parser.readToken(), ListBeginToken.instance);
      Assert.assertEquals(parser.readToken(), new WordToken("word"));
      Assert.assertEquals(parser.readToken(), new NumberToken(22));
      Assert.assertEquals(parser.readToken(), new StringToken("string 1:x"));
      Assert.assertEquals(parser.readToken(), new StringToken(" "));
      Assert.assertEquals(parser.readToken(), new StringToken("Тест"));
      Assert.assertEquals(parser.readToken(), ListBeginToken.instance);
      Assert.assertEquals(parser.readToken(), new WordToken("sublist"));
      Assert.assertEquals(parser.readToken(), ListEndToken.instance);
      Assert.assertEquals(parser.readToken(), ListEndToken.instance);
      Assert.assertNull(parser.readToken());
      Assert.assertNull(parser.readToken());
    }
  }

  @SuppressWarnings("MagicNumber")
  @Test
  public void testSimpleParseSmallBuffer() throws IOException {
    try (InputStream stream = new ByteArrayInputStream("( word 22 10:string 1:x 1:  8:Тест ( sublist ) ) ".getBytes(StandardCharsets.UTF_8))) {
      final SvnServerParser parser = new SvnServerParser(stream, 1);
      Assert.assertEquals(parser.readToken(), ListBeginToken.instance);
      Assert.assertEquals(parser.readToken(), new WordToken("word"));
      Assert.assertEquals(parser.readToken(), new NumberToken(22));
      Assert.assertEquals(parser.readToken(), new StringToken("string 1:x"));
      Assert.assertEquals(parser.readToken(), new StringToken(" "));
      Assert.assertEquals(parser.readToken(), new StringToken("Тест"));
      Assert.assertEquals(parser.readToken(), ListBeginToken.instance);
      Assert.assertEquals(parser.readToken(), new WordToken("sublist"));
      Assert.assertEquals(parser.readToken(), ListEndToken.instance);
      Assert.assertEquals(parser.readToken(), ListEndToken.instance);
      Assert.assertNull(parser.readToken());
      Assert.assertNull(parser.readToken());
    }
  }

  @Test
  public void testMessageParse() throws IOException {
    try (InputStream stream = new ByteArrayInputStream("( 2 ( edit-pipeline svndiff1 absent-entries depth mergeinfo log-revprops ) 15:svn://localhost 31:SVN/1.8.8 (x86_64-pc-linux-gnu) ( ) ) test ".getBytes(StandardCharsets.UTF_8))) {
      final SvnServerParser parser = new SvnServerParser(stream);
      AuthInfoReq req = MessageParser.parse(AuthInfoReq.class, parser);
      Assert.assertEquals(req.getProtocolVersion(), 2);
      Assert.assertEquals(req.getUrl(), "svn://localhost");
      Assert.assertEquals(req.getClientInfo(), "SVN/1.8.8 (x86_64-pc-linux-gnu)");
      ArrayAsserts.assertArrayEquals(new String[]{
          "edit-pipeline",
          "svndiff1",
          "absent-entries",
          "depth",
          "mergeinfo",
          "log-revprops"
      }, req.getCapabilities());
      Assert.assertEquals(parser.readText(), "test");
    }
  }

  @Test
  public void testMessageParse2() throws IOException {
    try (InputStream stream = new ByteArrayInputStream("( 2 ( edit-pipeline svndiff1 absent-entries depth mergeinfo log-revprops ) 15:svn://localhost ) test ".getBytes(StandardCharsets.UTF_8))) {
      final SvnServerParser parser = new SvnServerParser(stream);
      AuthInfoReq req = MessageParser.parse(AuthInfoReq.class, parser);
      Assert.assertEquals(req.getProtocolVersion(), 2);
      Assert.assertEquals(req.getUrl(), "svn://localhost");
      Assert.assertEquals(req.getClientInfo(), "");
      ArrayAsserts.assertArrayEquals(new String[]{
          "edit-pipeline",
          "svndiff1",
          "absent-entries",
          "depth",
          "mergeinfo",
          "log-revprops"
      }, req.getCapabilities());
      Assert.assertEquals(parser.readText(), "test");
    }
  }
  @Test
  public void testBinaryData() throws IOException {
    @SuppressWarnings("MagicNumber") final byte[] data = new byte[0x100];
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) i;
    }
    final byte[] streamData;
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      final SvnServerWriter writer = new SvnServerWriter(outputStream);
      writer.write(new StringToken(data));
      writer.write(new StringToken(data));
      writer.write(new WordToken("end"));
      streamData = outputStream.toByteArray();
    }
    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(streamData)) {
      final SvnServerParser parser = new SvnServerParser(inputStream);
      Assert.assertEquals(parser.readToken(), new StringToken(data));
      Assert.assertEquals(parser.readToken(), new StringToken(data));
      Assert.assertEquals(parser.readToken(), new WordToken("end"));
    }
  }
}
