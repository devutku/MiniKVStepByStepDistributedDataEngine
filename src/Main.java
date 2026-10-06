import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        // 1. Leader için storage başlat
        MiniKVStorage storage = new MiniKVStorage("leader_data.db");

        // 2. Sunucuyu LEADER rolüyle 8080 portunda aç
        MiniKVHttpServer server = new MiniKVHttpServer(8080, storage, NodeRole.LEADER);

        // 3. İleride açılacak Follower'ın adresini kaydet
        server.addFollower("http://127.0.0.1:8081");

        // 4. Sunucuyu başlat
        server.start();

        System.out.println(">>> SADECE LEADER ÇALIŞIYOR (Port: 8080) <<<");

        // Uygulama kapandığında dosya kilidini ve sunucuyu temiz kapat
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            try {
                storage.close();
            } catch (IOException ignored) {}
        }));
    }
}