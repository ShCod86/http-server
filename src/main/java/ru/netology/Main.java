package ru.netology;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        final List<String> validPaths = List.of(
                "/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js",
                "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
        Server server = new Server();

        for (String validPath : validPaths) {
            server.addHandler("GET", validPath, server.defaultHandler);
        }
        server.addHandler("GET", "/classic.html", (request, responseStream) -> {
            try {
                final Path filePath = Path.of(".", "public", "/classic.html");
                final String mimeType = Files.probeContentType(filePath);
                final String template = Files.readString(filePath);
                final byte[] content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();

                responseStream.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                responseStream.write(content);
                responseStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        server.listen(8081);
    }
}

