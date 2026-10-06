import java.io.IOException;

public class FollowerMain {
    public static void main(String[] args) throws IOException {
        // 1. Follower için bağımsız storage (kendi disk dosyası)
        MiniKVStorage storage = new MiniKVStorage("follower_data.db");

        // 2. Sunucuyu FOLLOWER rolüyle 8081 portunda aç
        MiniKVHttpServer server = new MiniKVHttpServer(8081, storage, NodeRole.FOLLOWER);

        // 3. Sunucuyu başlat
        server.start();

        System.out.println(">>> SADECE FOLLOWER ÇALIŞIYOR (Port: 8081) <<<");

        // Kapanırken dosyaları temiz kapat
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            try {
                storage.close();
            } catch (IOException ignored) {}
        }));
    }
}