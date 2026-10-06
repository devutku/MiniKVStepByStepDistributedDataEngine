public class ReplicationTask {
    private final String action;
    private final String key;
    private final String value;
    private final long timestamp;

    public ReplicationTask(String action, String key, String value) {
        this.action = action;
        this.key = key;
        this.value = value;
        this.timestamp = System.currentTimeMillis();
    }

    public String getAction() { return action; }
    public String getKey() { return key; }
    public String getValue() { return value; }
    public long getTimestamp() { return timestamp; }

    public String toPayload() {
        return action + ":" + key + (value != null ? ":" + value : "");
    }
}