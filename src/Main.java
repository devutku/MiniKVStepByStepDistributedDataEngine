import java.io.IOException;
    public class Main {
        public static void main(String[] args) {
            String dbPath = "data.db";

            try (MiniKVStorage storage = new MiniKVStorage(dbPath)) {
                // 1. İlk yazma
                storage.set("user_1", "Ahmet");
                System.out.println("İlk okuma: " + storage.get("user_1"));

                // 2. Aynı anahtarı güncelleme (Append-only mantığı)
                storage.set("user_1", "Mehmet");
                System.out.println("Güncelleme sonrası: " + storage.get("user_1"));

                // 3. Yeni bir anahtar ekleme
                storage.set("role", "Engineer");
                System.out.println("Role: " + storage.get("role"));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
