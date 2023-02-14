package dev.drawethree.xprison.ranks.repo.impl;

import dev.drawethree.xprison.database.RedisDatabase;
import dev.drawethree.xprison.database.RedisKeys;
import dev.drawethree.xprison.ranks.repo.RanksRepository;
import org.bukkit.OfflinePlayer;

public class RedisRanks implements RanksRepository {
    private final RedisDatabase database;

    public RedisRanks(RedisDatabase database) {
        this.database = database;
    }

    @Override
    public int getPlayerRank(OfflinePlayer player) {
        return ((int) database.getByKey(RedisKeys.RANKS, player.getUniqueId()));
    }

    @Override
    public void updateRank(OfflinePlayer player, int rank) {
        database.setByKey(RedisKeys.RANKS, player.getUniqueId(), rank);
    }

    @Override
    public void addIntoRanks(OfflinePlayer player) {
        database.setByKey(RedisKeys.RANKS, player.getUniqueId(), 0);
    }

    @Override
    public void createTables() {
        // Ignored
    }

    @Override
    public void clearTableData() {
        database.clear(RedisKeys.RANKS);
    }
}
