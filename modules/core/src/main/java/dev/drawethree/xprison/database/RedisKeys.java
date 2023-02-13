package dev.drawethree.xprison.database;

public enum RedisKeys {
    SHARDS("xprison:shards"),
    BLOCKS("xprison:blocks");

    private final String path;

    RedisKeys(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
