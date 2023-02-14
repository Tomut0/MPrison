package dev.drawethree.xprison.tokens.repo.impl;

import dev.drawethree.xprison.database.RedisDatabase;
import dev.drawethree.xprison.database.RedisKeys;
import dev.drawethree.xprison.tokens.repo.BlocksRepository;
import org.bukkit.OfflinePlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RedisBlocks implements BlocksRepository {

    private final RedisDatabase database;

    public RedisBlocks(RedisDatabase database) {
        this.database = database;
    }

    @Override
    public void resetBlocksWeekly() {
        // ignored
    }

    @Override
    public void updateBlocks(OfflinePlayer player, long newAmount) {
        database.setByKey(RedisKeys.BLOCKS, player.getUniqueId(), newAmount);
    }

    @Override
    public void updateBlocksWeekly(OfflinePlayer player, long newAmount) {
        // ignored
    }

    @Override
    public long getPlayerBrokenBlocksWeekly(OfflinePlayer player) {
        return 0;
    }

    @Override
    public void addIntoBlocks(OfflinePlayer player) {
        // equals `create blocks`
        database.setByKey(RedisKeys.BLOCKS, player.getUniqueId(), 0);
    }

    @Override
    public void addIntoBlocksWeekly(OfflinePlayer player) {
        // ignored
    }

    @Override
    public long getPlayerBrokenBlocks(OfflinePlayer player) {
        return ((long) database.getByKey(RedisKeys.BLOCKS, player.getUniqueId()));
    }

    @Override
    public Map<UUID, Long> getTopBlocksWeekly(int amountOfRecords) {
        return new HashMap<>();
    }

    @Override
    public Map<UUID, Long> getTopBlocks(int amountOfRecords) {
        return database.getTopTen(RedisKeys.BLOCKS);
    }

    @Override
    public void createTables() {
        // ignored
    }

    @Override
    public void clearTableData() {
        database.clear(RedisKeys.BLOCKS);
    }
}
