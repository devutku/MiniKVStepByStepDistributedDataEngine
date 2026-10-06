void main() throws IOException {
    String dbPath = "data.db";

    try (MiniKVStorage storage = new MiniKVStorage(dbPath)) {
        // 1. İlk yazma
        storage.set("user_1", "Ahmet");

        // 2. Aynı anahtarı güncelleme (Append-only mantığı)
        storage.set("user_1", "Mehmet");

        // 3. Yeni bir anahtar ekleme
        storage.set("role", "Engineer");
        IO.println("Role: " + storage.get("role"));

    }
}
