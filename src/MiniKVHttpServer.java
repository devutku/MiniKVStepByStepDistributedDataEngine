import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class MiniKVHttpServer {
    private final HttpServer server;
    private final MiniKVStorage storage;

    public MiniKVHttpServer(int port, MiniKVStorage storage) throws IOException {
        this.storage = storage;
        // 0 backlog: işletim sisteminin varsayılan kuyruk boyutunu kullanır
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        // Routing: /kv endpoint'ini KVHandler'a yönlendir
        this.server.createContext("/kv", new KVHandler());

        // Gelen eşzamanlı istekler için thread pool (örneğin 10 worker thread)
        this.server.setExecutor(Executors.newFixedThreadPool(10));
    }

    public void start() {
        server.start();
        System.out.println("MiniKV HTTP Sunucusu ayakta: http://localhost:" + server.getAddress().getPort() + "/kv");
    }

    public void stop() {
        server.stop(0);
    }

    private class KVHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath(); // Örn: /kv/user_1
            String[] segments = path.split("/");

            try {
                if ("GET".equalsIgnoreCase(method)) {
                    handleGet(exchange, segments);
                } else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                    if (segments.length >= 2 && "compact".equalsIgnoreCase(segments[1])) {
                        // POST /compact çağrıldığında
                        storage.compact();
                        sendResponse(exchange, 200, "Compaction tamamlandi. Dosya optimize edildi.");
                    } else {
                        handleSet(exchange, segments);
                    }
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    handleDelete(exchange, segments);
                } else {
                    sendResponse(exchange, 405, "Method Not Allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }

        // GET /kv/{key}
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

        // POST /kv/{key} -> Body: {value}
        private void handleSet(HttpExchange exchange, String[] segments) throws IOException {
            if (segments.length < 3 || segments[2].isBlank()) {
                sendResponse(exchange, 400, "Hata: Key belirtilmedi. Örnek: /kv/user_1");
                return;
            }

            String key = segments[2];

            // HTTP Request Body'den value'yu oku
            InputStream is = exchange.getRequestBody();
            String value = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            if (value.isBlank()) {
                sendResponse(exchange, 400, "Hata: Request body (değer) boş olamaz.");
                return;
            }

            storage.set(key, value);
            sendResponse(exchange, 201, "OK: " + key + " kaydedildi.");
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
            byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        private void handleDelete(HttpExchange exchange, String[] segments) throws IOException {
            if (segments.length < 3 || segments[2].isBlank()) {
                sendResponse(exchange, 400, "Hata: Key belirtilmedi. Örnek: /kv/user_1");
                return;
            }

            String key = segments[2];
            boolean deleted = storage.delete(key);

            if (deleted) {
                sendResponse(exchange, 200, "OK: " + key + " silindi.");
            } else {
                sendResponse(exchange, 404, "Silinemedi: Key bulunamadi.");
            }
        }
    }
}