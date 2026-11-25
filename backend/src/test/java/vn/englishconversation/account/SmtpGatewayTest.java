package vn.englishconversation.account;

import vn.englishconversation.mail.provider.SmtpMailGateway;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class SmtpGatewayTest {
  @Test
  void sendsToLocalSmtpOnlyAndDisabledGatewayDoesNotSend() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var received =
          executor.submit(
              () -> {
                try (var socket = server.accept()) {
                  socket.setSoTimeout(5000);
                  var input =
                      new BufferedReader(
                          new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                  var output =
                      new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                  output.print("220 localhost test SMTP\r\n");
                  output.flush();
                  var transcript = new StringBuilder();
                  boolean data = false;
                  for (String line; (line = input.readLine()) != null; ) {
                    transcript.append(line).append('\n');
                    if (data) {
                      if (line.equals(".")) {
                        data = false;
                        output.print("250 accepted\r\n");
                      } else continue;
                    } else if (line.startsWith("EHLO")) output.print("250 localhost\r\n");
                    else if (line.startsWith("DATA")) {
                      data = true;
                      output.print("354 send message\r\n");
                    } else if (line.startsWith("QUIT")) {
                      output.print("221 bye\r\n");
                      output.flush();
                      break;
                    } else output.print("250 OK\r\n");
                    output.flush();
                  }
                  return transcript.toString();
                }
              });
      var gateway =
          new SmtpMailGateway(
              true, "127.0.0.1", server.getLocalPort(), "", "", "sender@example.test", false);
      gateway.send("recipient@example.test", "Synthetic reminder", "Test local SMTP only");
      String result = received.get(8, TimeUnit.SECONDS);
      assertTrue(result.contains("RCPT TO:<recipient@example.test>"));
      assertTrue(result.contains("Subject: Synthetic reminder"));
      assertTrue(result.contains("Test local SMTP only"));
      var disabled = new SmtpMailGateway(false, "", 587, "", "", "", true);
      assertFalse(disabled.available());
      assertThrows(
          IllegalStateException.class, () -> disabled.send("nobody@example.test", "x", "x"));
    }
  }
}
