package dev.drawethree.xprison.tokens.repo.impl;

import dev.drawethree.xprison.database.RedisDatabase;
import dev.drawethree.xprison.database.RedisKeys;
import dev.drawethree.xprison.tokens.repo.TokensRepository;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public class RedisTokens implements TokensRepository {

    private final RedisDatabase database;

    public RedisTokens(@NotNull RedisDatabase database) {
        this.database = database;
    }

    @Override
    public long getPlayerTokens(OfflinePlayer player) {
        return ((long) database.getByKey(RedisKeys.SHARDS, player.getUniqueId()));
    }

    @Override
    public void updateTokens(OfflinePlayer player, long newAmount) {
        database.setByKey(RedisKeys.SHARDS, player.getUniqueId(), newAmount);
    }

    @Override
    public Map<UUID, Long> getTopTokens(int amountOfRecords) {
        return database.getTopTen(RedisKeys.SHARDS);
    }

    @Override
    public void addIntoTokens(OfflinePlayer player, long startingTokens) {
        database.setByKey(RedisKeys.SHARDS, player.getUniqueId(), startingTokens);
    }

    @Override
    public void createTables() {
        // ignored
    }

    @Override
    public void clearTableData() {
        database.clear(RedisKeys.SHARDS);
    }
}
