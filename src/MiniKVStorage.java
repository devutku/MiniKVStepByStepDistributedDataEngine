import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiniKVStorage implements AutoCloseable {
    private RandomAccessFile dbFile;
    private final File file;
    private final Map<String, RecordMetadata> index = new ConcurrentHashMap<>();

    public MiniKVStorage(String filePath) throws IOException {
        this.file = new File(filePath);
        this.dbFile = new RandomAccessFile(this.file, "rw");
        buildIndexFromDisk();
    }

    public synchronized void set(String key, String value) throws IOException {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        long currentOffset = dbFile.length();
        dbFile.seek(currentOffset);

        dbFile.writeInt(keyBytes.length);
        dbFile.writeInt(valueBytes.length);
        dbFile.write(keyBytes);
        dbFile.write(valueBytes);

        long valueOffset = currentOffset + 8 + keyBytes.length;
        index.put(key, new RecordMetadata(valueOffset, valueBytes.length));
    }

    public synchronized String get(String key) throws IOException {
        RecordMetadata meta = index.get(key);
        if (meta == null) {
            return null;
        }

        dbFile.seek(meta.getOffset());
        byte[] valueBuffer = new byte[meta.getValueLength()];
        dbFile.readFully(valueBuffer);

        return new String(valueBuffer, StandardCharsets.UTF_8);
    }

    /**
     * Tombstone Yazma: Değer uzunluğu -1 olarak diske eklenir.
     */
    public synchronized boolean delete(String key) throws IOException {
        if (!index.containsKey(key)) {
            return false; // Silinecek key zaten yok
        }

        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        long currentOffset = dbFile.length();
        dbFile.seek(currentOffset);

        // Header: KeyLen, ValueLen = -1 (Tombstone bayrağı)
        dbFile.writeInt(keyBytes.length);
        dbFile.writeInt(-1);
        dbFile.write(keyBytes);

        // Bellekteki indeksten kaldır
        index.remove(key);
        return true;
    }

    /**
     * Compaction (Temizlik): Sadece canlı verileri yeni bir dosyaya aktarır.
     */
    public synchronized void compact() throws IOException {
        File compactFile = new File(file.getAbsolutePath() + ".compact");
        if (compactFile.exists()) {
            compactFile.delete();
        }

        Map<String, RecordMetadata> newIndex = new ConcurrentHashMap<>();

        try (RandomAccessFile compactDb = new RandomAccessFile(compactFile, "rw")) {
            for (Map.Entry<String, RecordMetadata> entry : index.entrySet()) {
                String key = entry.getKey();
                RecordMetadata oldMeta = entry.getValue();

                // Eski dosyadan canlı değeri oku
                dbFile.seek(oldMeta.getOffset());
                byte[] valBytes = new byte[oldMeta.getValueLength()];
                dbFile.readFully(valBytes);

                byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

                // Yeni kompakt dosyaya yaz
                long newOffset = compactDb.length();
                compactDb.seek(newOffset);
                compactDb.writeInt(keyBytes.length);
                compactDb.writeInt(valBytes.length);
                compactDb.write(keyBytes);
                compactDb.write(valBytes);

                long newValueOffset = newOffset + 8 + keyBytes.length;
                newIndex.put(key, new RecordMetadata(newValueOffset, valBytes.length));
            }
        }

        // Mevcut dosyayı kapatıp yeni dosya ile değiştir
        dbFile.close();
        Files.move(compactFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        // Yeni dosyayı tekrar aç ve indeksi güncelle
        this.dbFile = new RandomAccessFile(this.file, "rw");
        this.index.clear();
        this.index.putAll(newIndex);
    }

    private void buildIndexFromDisk() throws IOException {
        long currentPos = 0;
        long fileLength = dbFile.length();

        while (currentPos < fileLength) {
            dbFile.seek(currentPos);
            int keyLen = dbFile.readInt();
            int valLen = dbFile.readInt();

            byte[] keyBytes = new byte[keyLen];
            dbFile.readFully(keyBytes);
            String key = new String(keyBytes, StandardCharsets.UTF_8);

            if (valLen == -1) {
                // Disk taranırken Tombstone görüldüyse indeksten çıkar
                index.remove(key);
                currentPos = currentPos + 8 + keyLen;
            } else {
                long valueOffset = currentPos + 8 + keyLen;
                index.put(key, new RecordMetadata(valueOffset, valLen));
                currentPos = valueOffset + valLen;
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (dbFile != null) {
            dbFile.close();
        }
    }
}