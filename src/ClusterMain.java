import java.io.IOException;

public class ClusterMain {
    public static void main(String[] args) {
        try {
            // 1. Leader Kurulumu (Port 8080)
            MiniKVStorage leaderStorage = new MiniKVStorage("leader_data.db");
            MiniKVHttpServer leaderServer = new MiniKVHttpServer(8080, leaderStorage, NodeRole.LEADER);

            // Leader, Follower'ın adresini biliyor
            leaderServer.addFollower("http://localhost:8081");
            leaderServer.start();

            // 2. Follower Kurulumu (Port 8081)
            MiniKVStorage followerStorage = new MiniKVStorage("follower_data.db");
            MiniKVHttpServer followerServer = new MiniKVHttpServer(8081, followerStorage, NodeRole.FOLLOWER);
            followerServer.start();

            System.out.println("\n>>> Kume (Cluster) hazir: Leader (8080), Follower (8081) <<<\n");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                leaderServer.stop();
                followerServer.stop();
                try {
                    leaderStorage.close();
                    followerStorage.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}