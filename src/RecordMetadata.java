public class RecordMetadata {
    private final long offset;
    private final int valueLength;

    public RecordMetadata(long offset, int valueLength) {
        this.offset = offset;
        this.valueLength = valueLength;
    }

    public long getOffset() { return offset; }
    public int getValueLength() { return valueLength; }
}