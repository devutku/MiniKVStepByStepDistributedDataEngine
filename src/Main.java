import java.io.IOException;

public class Main {
    void main() throws IOException {
    /*String dbPath = "data.db";

    try (MiniKVStorage storage = new MiniKVStorage(dbPath)) {
        // 1. İlk yazma
        storage.set("user_1", "Ahmet");

        // 2. Aynı anahtarı güncelleme (Append-only mantığı)
        storage.set("user_1", "Mehmet");

        // 3. Yeni bir anahtar ekleme
        storage.set("role", "Engineer");
        IO.println("Role: " + storage.get("role"));

    }*/
        String dbPath = "data.db";
        int port = 8080;

        try {
            // Depolama motorunu başlat
            MiniKVStorage storage = new MiniKVStorage(dbPath);

            // HTTP API katmanını motor ile bağla
            MiniKVHttpServer server = new MiniKVHttpServer(port, storage, NodeRole.FOLLOWER);
            server.start();

            // JVM kapandığında dosyaları düzgün kapatmak için shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Sunucu kapatılıyor...");
                server.stop();
                try {
                    storage.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
