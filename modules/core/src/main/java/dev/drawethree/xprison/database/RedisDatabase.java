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
        config.setMaxTotal(16);

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

    public void clear() {
        try (Jedis resource = jedisPool.getResource()) {
            resource.hdel(SHARDS.getPath());
            resource.hdel(BLOCKS.getPath());
        } catch (Exception er) {
            er.printStackTrace();
        }
    }

    public int getByKey(@NotNull RedisKeys key, @NotNull UUID uuid) {
        int shards = 0;
        try (Jedis resource = jedisPool.getResource()) {
            return Integer.parseInt(resource.hget(key.getPath(), uuid.toString()));
        } catch (Exception er) {
            er.printStackTrace();
        }

        return shards;
    }

    public void updateByKey(@NotNull RedisKeys key, @NotNull UUID uuid, double value) {
        try (Jedis resource = jedisPool.getResource()) {
            resource.zincrby(key.getPath(), value, uuid.toString());
            XPrison.getInstance().getLogger().info(String.format("Updated %s - %s", uuid, value));
        } catch (Exception er) {
            er.printStackTrace();
        }
    }

    public void setByKey(@NotNull RedisKeys key, @NotNull UUID uuid, double value) {
        try (Jedis resource = jedisPool.getResource()) {
            resource.zadd(key.getPath(), value, uuid.toString());
            XPrison.getInstance().getLogger().info(String.format("Set %s - %s", uuid, value));
        } catch (Exception er) {
            er.printStackTrace();
        }
    }

    public Map<UUID, Long> getTopTen(@NotNull RedisKeys key) {
        Map<UUID, Long> top = new HashMap<>();

        try (Jedis resource = jedisPool.getResource()) {
            List<Tuple> tuples = resource.zrevrangeWithScores(key.getPath(), 10, 0);
            for (Tuple tuple : tuples) {
                top.put(UUID.fromString(tuple.getElement()), ((long) tuple.getScore()));
            }

            return top;
        } catch (Exception er) {
            er.printStackTrace();
        }

        return top;
    }

    public void insertShardsAndBlocks(@NotNull UUID uuid, double shards, int blocks, int tries) {
        try (Jedis resource = jedisPool.getResource()) {
            Pipeline pipe = resource.pipelined();
            pipe.hset(SHARDS.getPath(), uuid.toString(), String.valueOf(shards));
            pipe.hset(BLOCKS.getPath(), uuid.toString(), String.valueOf(blocks));
        } catch (Exception e) {
            if (tries < 3) {
                e.printStackTrace();
                String playerName = Bukkit.getOfflinePlayer(uuid).getName();
                XPrison.getInstance().getLogger().severe("Failed to insert shards/blocks into account " + playerName + " after" + tries + " tries");
                insertShardsAndBlocks(uuid, shards, blocks, tries - 1);
            } else {
                e.printStackTrace();
            }
        }
    }

    public void close() {
        jedisPool.close();
    }
}
