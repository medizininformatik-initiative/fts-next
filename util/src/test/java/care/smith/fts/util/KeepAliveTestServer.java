package care.smith.fts.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal HTTP/1.1 test server that holds every connection open as a persistent keep-alive socket,
 * serving any number of requests on the same connection. It never closes a connection itself, so a
 * client is free to reuse a pooled socket indefinitely.
 *
 * <p>This lets a test distinguish connection <em>reuse</em> from a <em>fresh</em> connection purely
 * by counting accepts: if the outbound pool reuses an idle socket the count stays at one; if the
 * pool evicts the idle socket (e.g. because its {@code maxIdleTime} elapsed) the next request opens
 * a new connection and the count rises. See {@link #connectionsAccepted()}.
 */
final class KeepAliveTestServer implements AutoCloseable {

  private final ServerSocket serverSocket;
  private final ExecutorService workers =
      Executors.newCachedThreadPool(
          r -> {
            var t = new Thread(r, "keep-alive-test-server");
            t.setDaemon(true);
            return t;
          });
  private final AtomicInteger connectionsAccepted = new AtomicInteger();

  KeepAliveTestServer() throws IOException {
    serverSocket = new ServerSocket();
    serverSocket.bind(new InetSocketAddress("localhost", 0));
    workers.submit(this::acceptLoop);
  }

  String baseUrl() {
    return "http://localhost:" + serverSocket.getLocalPort();
  }

  int connectionsAccepted() {
    return connectionsAccepted.get();
  }

  private void acceptLoop() {
    while (!serverSocket.isClosed()) {
      try {
        Socket socket = serverSocket.accept();
        connectionsAccepted.incrementAndGet();
        workers.submit(() -> servePersistently(socket));
      } catch (IOException e) {
        return; // server socket closed → stop accepting
      }
    }
  }

  private void servePersistently(Socket socket) {
    try (socket) {
      InputStream in = socket.getInputStream();
      OutputStream out = socket.getOutputStream();
      while (readRequestHeaders(in)) {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String head =
            "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: "
                + body.length
                + "\r\n"
                + "Connection: keep-alive\r\n"
                + "\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
      }
    } catch (IOException ignored) {
      // client closed the connection or the server is shutting down
    }
  }

  /**
   * Reads and discards one request's headers up to the blank line that terminates them. Returns
   * {@code false} when the stream reaches EOF before a full request arrives (client hung up).
   */
  private static boolean readRequestHeaders(InputStream in) throws IOException {
    int matched = 0; // chars of the \r\n\r\n terminator matched so far
    int b;
    while (matched < 4 && (b = in.read()) != -1) {
      boolean expectCarriageReturn = matched == 0 || matched == 2;
      if ((expectCarriageReturn && b == '\r') || (!expectCarriageReturn && b == '\n')) {
        matched++;
      } else {
        matched = (b == '\r') ? 1 : 0;
      }
    }
    return matched == 4;
  }

  @Override
  public void close() throws IOException {
    serverSocket.close();
    workers.shutdownNow();
  }
}
