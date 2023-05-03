package ru.netology;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;


public class Server {
    private final ExecutorService threadPool = Executors.newFixedThreadPool(64);
    private final Map<String, Map<String, Handler>> handlers = new ConcurrentHashMap<>();

    public void listen(int port) {
        System.out.println("Start server...");
        try (ServerSocket server = new ServerSocket(port)) {
            while (true) {
                threadPool.submit(new Connection(server.accept()));
                System.out.println("new connection");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        Map<String, Handler> map = new ConcurrentHashMap<>();
        if (handlers.containsKey(method)) {
            map = handlers.get(method);
        }
        map.put(path, handler);
        handlers.put(method, map);
    }
    Handler defaultHandler = (request, responseStream) -> {
        try {
            final Path filePath = Path.of(".", "public", request.getRequestLine().getPath());
            final String mimeType = Files.probeContentType(filePath);

            final long length = Files.size(filePath);
            responseStream.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, responseStream);
            responseStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    };



    private class Connection implements Runnable {
        private BufferedInputStream in;
        private BufferedOutputStream out;
        private final Socket socket;
        private final byte[] requestLineDelimiter = new byte[]{'\r', '\n'};
        private final byte[] headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};

        public Connection(Socket socket) {
            this.socket = socket;
        }
        @Override
        public void run() {
            while (true) {
                try {
                    in = new BufferedInputStream(socket.getInputStream());
                    out = new BufferedOutputStream(socket.getOutputStream());
                    // лимит на request line + заголовки
                    final int limit = 4096;
                    in.mark(limit);
                    final byte[] buffer = new byte[limit];
                    final int read = in.read(buffer);

                    RequestLine requestLine = getRequestLine(buffer, read);
                    if (requestLine == null) {
                        badRequest(out);
                        return;
                    }
                    List<String> headers = getHeaders(buffer, read, in);
                    if (headers == null) {
                        badRequest(out);
                        return;
                    }
                    Request request = new Request(requestLine, headers);
                    request.setBody(getBody(request, in));
                    request.setQueryParams(getQueryParams(requestLine.getPath()));
                    start(request, out);

                } catch (IOException | URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        }
        private void start(Request request, BufferedOutputStream out) throws IOException {
            Handler handler = handlers.get(request.getRequestLine().getMethod())
                    .get(request.getRequestLine().getPath());

            if (handler != null) {
                handler.handle(request, out);
            } else {
                badRequest(out);
            }
        }

        private RequestLine getRequestLine(byte[] buffer, int read) throws IOException {
            // ищем request line
            final int requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                return null;
            }

            // читаем request line
            final String[] parts = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (parts.length != 3) {
                return null;
            }

            // проверяем, валидный ли путь
            if (!parts[1].startsWith("/")) {
                return null;
            }

            return new RequestLine(parts[0], parts[1], parts[2]);
        }

        private List<String> getHeaders(byte[] buffer, int read, BufferedInputStream in) throws IOException {
            final int requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            // ищем заголовки
            final int headersStart = requestLineEnd + requestLineDelimiter.length;
            final int headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                return null;
            }
            in.reset();

            in.skip(headersStart);

            final byte[] headersBytes = in.readNBytes(headersEnd - headersStart);
            return Arrays.asList(new String(headersBytes).split("\r\n"));
        }

        private String getBody(Request request, BufferedInputStream in) throws IOException {
            // для GET тело МОЖЕТ быть, но общепринято его игнорировать
            if (!request.getRequestLine().getMethod().equals("GET")) {
                in.skip(headersDelimiter.length);
                // вычитываем Content-Length, чтобы прочитать body
                final Optional<String> contentLength = extractHeader(request.getHeaders(), "Content-Length");
                if (contentLength.isPresent()) {
                    final int length = Integer.parseInt(contentLength.get());
                    final byte[] body = in.readNBytes(length);
                    return new String(body);
                }
            }
            return null;
        }
        private List<NameValuePair> getQueryParams(String path) throws URISyntaxException {
            final URI uri = new URI(path);
            return URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
        }

        private void badRequest(BufferedOutputStream out) throws IOException {
            out.write((
                    "HTTP/1.1 400 Bad Request\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        }
        private Optional<String> extractHeader(List<String> headers, String header) {
            return headers.stream()
                    .filter(o -> o.startsWith(header))
                    .map(o -> o.substring(o.indexOf(" ")))
                    .map(String::trim)
                    .findFirst();
        }
        // from google guava with modifications
        private int indexOf(byte[] array, byte[] target, int start, int max) {
            outer:
            for (int i = start; i < max - target.length + 1; i++) {
                for (int j = 0; j < target.length; j++) {
                    if (array[i + j] != target[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
    }
}
