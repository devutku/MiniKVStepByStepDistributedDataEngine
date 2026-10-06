import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiniKVStorage implements AutoCloseable {
    private final RandomAccessFile dbFile;
    // RAM'de tutulan dizin (KeyDir)
    private final Map<String, RecordMetadata> index = new ConcurrentHashMap<>();

    public MiniKVStorage(String filePath) throws IOException {
        // "rw": Dosyayı hem okuma hem yazma modunda açar
        this.dbFile = new RandomAccessFile(filePath, "rw");
        buildIndexFromDisk();
    }

    /**
     * Format (Basit Binary / Length-Prefixed):
     * [Key Length (4 bayt)] [Value Length (4 bayt)] [Key Bytes] [Value Bytes]
     */
    public synchronized void set(String key, String value) throws IOException {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        // Dosyanın en sonuna git (Append-only)
        long currentOffset = dbFile.length();
        dbFile.seek(currentOffset);

        // Başlıkları ve veriyi yaz
        dbFile.writeInt(keyBytes.length);
        dbFile.writeInt(valueBytes.length);
        dbFile.write(keyBytes);
        dbFile.write(valueBytes);

        // Bellekteki haritayı güncelle (Değerin başladığı ofseti kaydediyoruz)
        // Ofset: Başlangıç + int(4) + int(4) + keyBytes.length
        long valueOffset = currentOffset + 8 + keyBytes.length;
        index.put(key, new RecordMetadata(valueOffset, valueBytes.length));
    }

    public synchronized String get(String key) throws IOException {
        RecordMetadata meta = index.get(key);
        if (meta == null) {
            return null; // Key bulunamadı
        }

        // Doğrudan değerin başladığı bayta atla
        dbFile.seek(meta.getOffset());
        byte[] valueBuffer = new byte[meta.getValueLength()];
        dbFile.readFully(valueBuffer);

        return new String(valueBuffer, StandardCharsets.UTF_8);
    }

    /**
     * Program yeniden başladığında diski baştan sona tarayıp RAM'deki haritayı yeniden kurar.
     */
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

            long valueOffset = currentPos + 8 + keyLen;
            index.put(key, new RecordMetadata(valueOffset, valLen));

            // Bir sonraki kayda atla
            currentPos = valueOffset + valLen;
        }
    }

    @Override
    public void close() throws IOException {
        dbFile.close();
    }
}