import java.io.IOException;

public class DockerContainerMain {
    public static void main(String[] args) throws IOException {
        // Ortam değişkenlerinden yapılandırmayı oku (yoksa varsayılanları kullan)
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String roleStr = System.getenv().getOrDefault("ROLE", "LEADER");
        String dataFile = System.getenv().getOrDefault("DATA_FILE", "data.db");
        String followerUrl = System.getenv().getOrDefault("FOLLOWER_URL", "");

        NodeRole role = NodeRole.valueOf(roleStr.toUpperCase());

        // Storage motorunu başlat
        MiniKVStorage storage = new MiniKVStorage(dataFile);

        // Sunucuyu başlat
        MiniKVHttpServer server = new MiniKVHttpServer(port, storage, role);

        // Eğer Leader ise ve Follower adresi verilmişse ekle
        if (role == NodeRole.LEADER && !followerUrl.isBlank()) {
            server.addFollower(followerUrl);
        }

        server.start();

        System.out.println(">>> MiniKV Düğümü Çalışıyor | Port: " + port + " | Rol: " + role + " | Dosya: " + dataFile + " <<<");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            try {
                storage.close();
            } catch (IOException ignored) {}
        }));
    }
}