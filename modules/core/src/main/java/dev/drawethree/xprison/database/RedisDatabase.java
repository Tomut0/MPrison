package dev.drawethree.xprison.database;

import dev.drawethree.xprison.XPrison;
import dev.drawethree.xprison.database.model.DatabaseCredentials;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.*;
import redis.clients.jedis.resps.Tuple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.drawethree.xprison.database.RedisKeys.BLOCKS;
import static dev.drawethree.xprison.database.RedisKeys.SHARDS;

public final class RedisDatabase {

    private final DatabaseCredentials credentials;
    @Getter
    private JedisPool jedisPool;

    public RedisDatabase(DatabaseCredentials credentials) {
        this.credentials = credentials;
        connect();
    }

    private void connect() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(XPrison.getInstance().getConfig().getInt("redis.maxtotal", 16));

        if (credentials.getPassword() != null && !credentials.getPassword().equals("")) {
            jedisPool = new JedisPool(config, credentials.getHost(), credentials.getPort(), 10000, credentials.getPassword());
        } else {
            jedisPool = new JedisPool(config, credentials.getHost(), credentials.getPort(), 10000);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
        } catch (Exception er) {
            er.printStackTrace();
        }
    }

    public void clear(RedisKeys key) {
        try (Jedis resource = jedisPool.getResource()) {
            resource.zrem(key.getPath());
        } catch (Exception er) {
            er.printStackTrace();
        }
    }

    public double getByKey(@NotNull RedisKeys key, @NotNull UUID uuid) {
        try (Jedis resource = jedisPool.getResource()) {
            Double zscore = resource.zscore(key.getPath(), uuid.toString());
            return zscore != null ? zscore : 0;
        } catch (Exception er) {
            er.printStackTrace();
        }

        return 0;
    }

    public void setByKey(@NotNull RedisKeys key, @NotNull UUID uuid, double value) {
        try (Jedis resource = jedisPool.getResource()) {
            resource.zadd(key.getPath(), value, uuid.toString());
            //XPrison.getInstance().getLogger().info(String.format("Set [%s] %s - %s", key.name(), uuid, value));
        } catch (Exception er) {
            er.printStackTrace();
        }
    }

    public Map<UUID, Long> getTopTen(@NotNull RedisKeys key) {
        Map<UUID, Long> top = new HashMap<>();

        try (Jedis resource = jedisPool.getResource()) {
            List<Tuple> tuples = resource.zrevrangeWithScores(key.getPath(), 0, 10);
            for (Tuple tuple : tuples) {
                top.put(UUID.fromString(tuple.getElement()), ((long) tuple.getScore()));
            }

            return top;
        } catch (Exception er) {
            er.printStackTrace();
        }

        return top;
    }

    public void close() {
        jedisPool.close();
    }
}
