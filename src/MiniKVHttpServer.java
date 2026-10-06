import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public class MiniKVHttpServer {
    private final HttpServer server;
    private final MiniKVStorage storage;

    // [YENİ 1]: Düğümün rolü (LEADER veya FOLLOWER)
    private final NodeRole role;
    // [YENİ 2]: Lider ise verinin replike edileceği takipçi adresleri
    private final List<String> followers = new CopyOnWriteArrayList<>();
    // [YENİ 3]: Takipçilere HTTP isteği atacak istemci
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public MiniKVHttpServer(int port, MiniKVStorage storage, NodeRole role) throws IOException {
        this.storage = storage;
        this.role = role;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        // Açık ve net endpoint rotaları:
        this.server.createContext("/kv", new KVHandler());
        this.server.createContext("/compact", new CompactHandler());
        this.server.createContext("/internal/replicate", new ReplicationHandler());

        this.server.setExecutor(Executors.newFixedThreadPool(10));
    }

    public void addFollower(String followerBaseUrl) {
        this.followers.add(followerBaseUrl);
    }

    public void start() {
        server.start();
        System.out.println("[" + role + "] MiniKV HTTP Sunucusu ayakta: http://localhost:" + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }

    // [YENİ 5]: Liderin tüm takipçilere arka planda asenkron veri göndermesi
    private void replicateToFollowers(String action, String key, String value) {
        for (String followerUrl : followers) {
            Executors.defaultThreadFactory().newThread(() -> {
                try {
                    String payload = action + ":" + key + (value != null ? ":" + value : "");
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(followerUrl + "/internal/replicate"))
                            .header("Content-Type", "text/plain")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();

                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                } catch (Exception e) {
                    System.err.println("Replikasyon iletim hatası (" + followerUrl + "): " + e.getMessage());
                }
            }).start();
        }
    }

    private class KVHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String[] segments = path.split("/");

            try {
                if ("GET".equalsIgnoreCase(method)) {
                    handleGet(exchange, segments);
                } else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                    // [YENİ 6]: Follower düğümüne dışarıdan doğrudan yazma engellenir
                    if (role == NodeRole.FOLLOWER) {
                        sendResponse(exchange, 403, "Forbidden: Follower dugumune dogrudan yazilamaz. Istegi Leader'a gonderin.");
                        return;
                    }
                    handleSet(exchange, segments);
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    // [YENİ 7]: Follower düğümünden doğrudan silme engellenir
                    if (role == NodeRole.FOLLOWER) {
                        sendResponse(exchange, 403, "Forbidden: Follower dugumunden dogrudan veri silinemez.");
                        return;
                    }
                    handleDelete(exchange, segments);
                } else {
                    sendResponse(exchange, 405, "Method Not Allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }

        private void handleGet(HttpExchange exchange, String[] segments) throws IOException {
            if (segments.length < 3 || segments[2].isBlank()) {
                sendResponse(exchange, 400, "Hata: Key belirtilmedi. Örnek: /kv/user_1");
                return;
            }

            String key = segments[2];
            String value = storage.get(key);

            if (value == null) {
                sendResponse(exchange, 404, "Key bulunamadi: " + key);
            } else {
                sendResponse(exchange, 200, value);
            }
        }

        private void handleSet(HttpExchange exchange, String[] segments) throws IOException {
            if (segments.length < 3 || segments[2].isBlank()) {
                sendResponse(exchange, 400, "Hata: Key belirtilmedi. Örnek: /kv/user_1");
                return;
            }

            String key = segments[2];
            InputStream is = exchange.getRequestBody();
            String value = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            if (value.isBlank()) {
                sendResponse(exchange, 400, "Hata: Request body (değer) boş olamaz.");
                return;
            }

            // 1. Kendi yerel diskine yaz
            storage.set(key, value);

            // [YENİ 8]: Lider ise işlemi takipçilere dağıt
            if (role == NodeRole.LEADER) {
                replicateToFollowers("SET", key, value);
            }

            sendResponse(exchange, 201, "OK: " + key + " kaydedildi.");
        }

        private void handleDelete(HttpExchange exchange, String[] segments) throws IOException {
            if (segments.length < 3 || segments[2].isBlank()) {
                sendResponse(exchange, 400, "Hata: Key belirtilmedi. Örnek: /kv/user_1");
                return;
            }

            String key = segments[2];
            boolean deleted = storage.delete(key);

            if (deleted) {
                // [YENİ 9]: Lider ise silme emrini de takipçilere dağıt
                if (role == NodeRole.LEADER) {
                    replicateToFollowers("DELETE", key, null);
                }
                sendResponse(exchange, 200, "OK: " + key + " silindi.");
            } else {
                sendResponse(exchange, 404, "Silinemedi: Key bulunamadi.");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
            byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // [YENİ 10]: Liderden gelen replikasyon akışını karşılayan iç handler
    private class ReplicationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            InputStream is = exchange.getRequestBody();
            String rawCommand = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String[] parts = rawCommand.split(":", 3);

            if (parts.length >= 2) {
                String action = parts[0];
                String key = parts[1];

                if ("SET".equals(action)) {
                    String value = parts.length > 2 ? parts[2] : "";
                    storage.set(key, value);
                    System.out.println("[REPLICATION] Follower diske yazdi: " + key + " -> " + value);
                } else if ("DELETE".equals(action)) {
                    storage.delete(key);
                    System.out.println("[REPLICATION] Follower diskten sildi: " + key);
                }
            }

            sendText(exchange, 200, "ACK");
        }

        private void sendText(HttpExchange exchange, int statusCode, String responseText) throws IOException {
            byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // [YENİ 11]: /compact endpoint'i için açık handler
    private class CompactHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    storage.compact();
                    byte[] bytes = "Compaction tamamlandi.".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } catch (Exception e) {
                    exchange.sendResponseHeaders(500, 0);
                    exchange.close();
                }
            } else {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
            }
        }
    }
}