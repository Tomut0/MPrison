package dev.drawethree.xprison.multipliers;

import dev.drawethree.ultrabackpacks.api.event.BackpackSellEvent;
import dev.drawethree.xprison.XPrison;
import dev.drawethree.xprison.XPrisonModule;
import dev.drawethree.xprison.api.enums.ReceiveCause;
import dev.drawethree.xprison.config.FileManager;
import dev.drawethree.xprison.multipliers.api.XPrisonMultipliersAPI;
import dev.drawethree.xprison.multipliers.api.XPrisonMultipliersAPIImpl;
import dev.drawethree.xprison.multipliers.api.events.PlayerMultiplierReceiveEvent;
import dev.drawethree.xprison.multipliers.enums.MultiplierType;
import dev.drawethree.xprison.multipliers.multiplier.GlobalMultiplier;
import dev.drawethree.xprison.multipliers.multiplier.Multiplier;
import dev.drawethree.xprison.multipliers.multiplier.PlayerMultiplier;
import dev.drawethree.xprison.multipliers.repo.MultipliersRepository;
import dev.drawethree.xprison.multipliers.repo.impl.MultipliersRepositoryImpl;
import dev.drawethree.xprison.multipliers.service.MultipliersService;
import dev.drawethree.xprison.multipliers.service.impl.MultipliersServiceImpl;
import dev.drawethree.xprison.tokens.api.events.PlayerTokensReceiveEvent;
import dev.drawethree.xprison.utils.player.PlayerUtils;
import dev.drawethree.xprison.utils.text.TextUtils;
import lombok.Getter;
import me.lucko.helper.Commands;
import me.lucko.helper.Events;
import me.lucko.helper.Schedulers;
import me.lucko.helper.event.filter.EventFilters;
import me.lucko.helper.scheduler.Task;
import me.lucko.helper.time.Time;
import me.lucko.helper.utils.Players;
import org.apache.commons.lang.StringUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class XPrisonMultipliers implements XPrisonModule {

    public static final String MODULE_NAME = "Multipliers";

    @Getter
    private static XPrisonMultipliers instance;
    @Getter
    private final XPrison core;
    @Getter
    private FileManager.Config config;
    @Getter
    private XPrisonMultipliersAPI api;
    private GlobalMultiplier globalSellMultiplier;
    private GlobalMultiplier globalTokenMultiplier;

    private Map<UUID, Multiplier> rankMultipliers;
    private Map<UUID, PlayerMultiplier> sellMultipliers;
    private Map<UUID, PlayerMultiplier> tokenMultipliers;

    private Map<String, String> messages;
    private Map<String, Double> permissionToMultiplier;

    private boolean enabled;

    private Task rankUpdateTask;
    private int rankMultiplierUpdateTime;

    @Getter
    private double globalSellMultiMax;
    @Getter
    private double globalTokenMultiMax;
    @Getter
    private double playerSellMultiMax;
    @Getter
    private double playerTokenMultiMax;

    @Getter
    private MultipliersRepository multipliersRepository;

    @Getter
    private MultipliersService multipliersService;

    public XPrisonMultipliers(XPrison plugin) {
        instance = this;
        core = plugin;
    }


    private void loadRankMultipliers() {
        permissionToMultiplier = new LinkedHashMap<>();

        ConfigurationSection section = getConfig().get().getConfigurationSection("ranks");

        if (section == null) {
            return;
        }

        boolean useLuckPerms = getConfig().get().getBoolean("use-luckperms-groups", false);

        String permPrefix = useLuckPerms ? "group." : "xprison.multiplier.";

        for (String rank : section.getKeys(false)) {
            String perm = permPrefix + rank;
            double multiplier = getConfig().get().getDouble("ranks." + rank);
            permissionToMultiplier.put(perm, multiplier);
            core.debug("Loaded Rank Multiplier '" + rank + "' with multiplier x" + multiplier + " (" + perm + ")", this);
        }

    }


    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void reload() {
        config.reload();

        loadMessages();
        loadRankMultipliers();

        rankMultiplierUpdateTime = getConfig().get().getInt("rank-multiplier-update-time", 5);
        globalSellMultiMax = getConfig().get().getDouble("global-multiplier.sell.max", 10.0);
        globalTokenMultiMax = getConfig().get().getDouble("global-multiplier.tokens.max", 10.0);
        playerSellMultiMax = getConfig().get().getDouble("sell-multiplier.max", 10.0);
        playerTokenMultiMax = getConfig().get().getDouble("token-multiplier.max", 10.0);
    }

    @Override
    public void enable() {

        enabled = true;
        config = core.getFileManager().getConfig("multipliers.yml").copyDefaults(true).save();

        rankMultipliers = new ConcurrentHashMap<>();
        sellMultipliers = new ConcurrentHashMap<>();
        tokenMultipliers = new ConcurrentHashMap<>();

        multipliersRepository = new MultipliersRepositoryImpl(core.getPluginDatabase());
        multipliersRepository.createTables();
        multipliersRepository.removeExpiredMultipliers();

        multipliersService = new MultipliersServiceImpl(multipliersRepository);

        rankMultiplierUpdateTime = getConfig().get().getInt("rank-multiplier-update-time", 5);
        globalSellMultiMax = getConfig().get().getDouble("global-multiplier.sell.max", 10.0);
        globalTokenMultiMax = getConfig().get().getDouble("global-multiplier.tokens.max", 10.0);
        playerSellMultiMax = getConfig().get().getDouble("sell-multiplier.max", 10.0);
        playerTokenMultiMax = getConfig().get().getDouble("token-multiplier.max", 10.0);

        loadMessages();
        loadRankMultipliers();
        registerCommands();
        registerEvents();
        removeExpiredMultipliers();
        loadGlobalMultipliers();
        loadOnlineMultipliers();
        api = new XPrisonMultipliersAPIImpl(this);

        rankUpdateTask = Schedulers.async().runRepeating(() -> Players.all().forEach(p -> this.rankMultipliers.put(p.getUniqueId(), this.calculateRankMultiplier(p))), this.rankMultiplierUpdateTime, TimeUnit.MINUTES, this.rankMultiplierUpdateTime, TimeUnit.MINUTES);
    }

    private void loadOnlineMultipliers() {
        Players.all().forEach(p -> {
            this.rankMultipliers.put(p.getUniqueId(), this.calculateRankMultiplier(p));
            this.loadSellMultiplier(p);
            this.loadTokenMultiplier(p);
        });
    }

    private void registerEvents() {
        Events.subscribe(PlayerJoinEvent.class)
                .handler(e -> {
                    rankMultipliers.put(e.getPlayer().getUniqueId(), calculateRankMultiplier(e.getPlayer()));
                    loadSellMultiplier(e.getPlayer());
                    loadTokenMultiplier(e.getPlayer());
                }).bindWith(core);
        Events.subscribe(PlayerQuitEvent.class)
                .handler(e -> {
                    this.rankMultipliers.remove(e.getPlayer().getUniqueId());
                    this.saveSellMultiplier(e.getPlayer(), true);
                    this.saveTokenMultiplier(e.getPlayer(), true);
                }).bindWith(core);

        Events.subscribe(PlayerTokensReceiveEvent.class, EventPriority.HIGHEST)
                .filter(EventFilters.ignoreCancelled())
                .handler(e -> {
                    OfflinePlayer p = e.getPlayer();
                    if (p.isOnline() && e.getCause() == ReceiveCause.MINING) {
                        e.setAmount((long) this.getApi().getTotalToDeposit((Player) p, e.getAmount(), MultiplierType.TOKENS));
                    }
                }).bindWith(core);

        if (this.core.isUltraBackpacksEnabled()) {
            Events.subscribe(BackpackSellEvent.class, EventPriority.NORMAL)
                    .handler(e -> {
                        double currentAmount = e.getMoneyToDeposit();
                        this.core.debug("BackpacksSellEvent >> Original Amount: " + currentAmount, this);
                        double newAmount = this.getApi().getTotalToDeposit(e.getPlayer(), currentAmount, MultiplierType.SELL);
                        this.core.debug("BackpacksSellEvent >> New Amount: " + newAmount, this);
                        e.setMoneyToDeposit(newAmount);
                    }).bindWith(core);
        }

    }

    private void saveSellMultiplier(Player player, boolean async) {

        PlayerMultiplier multiplier = this.sellMultipliers.remove(player.getUniqueId());

        if (async) {
            Schedulers.async().run(() -> {
                multipliersService.setSellMultiplier(player, multiplier);
                core.debug(String.format("Saved Sell Multiplier of player %s", player.getName()), this);
            });
        } else {
            multipliersService.setSellMultiplier(player, multiplier);
            core.debug(String.format("Saved Sell Multiplier of player %s", player.getName()), this);
        }
    }

    private void saveTokenMultiplier(Player player, boolean async) {

        PlayerMultiplier multiplier = tokenMultipliers.remove(player.getUniqueId());

        if (async) {
            Schedulers.async().run(() -> {
                this.multipliersService.setTokenMultiplier(player, multiplier);
                this.core.debug(String.format("Saved Token Multiplier of player %s", player.getName()), this);
            });
        } else {
            this.multipliersService.setTokenMultiplier(player, multiplier);
            this.core.debug(String.format("Saved Token Multiplier of player %s", player.getName()), this);
        }
    }

    private void loadGlobalMultipliers() {
        double multiSell = config.get().getDouble("global-multiplier.sell.multiplier");
        long timeLeftSell = config.get().getLong("global-multiplier.sell.timeLeft");

        double multiTokens = this.config.get().getDouble("global-multiplier.tokens.multiplier");
        long timeLeftTokens = this.config.get().getLong("global-multiplier.tokens.timeLeft");

        globalSellMultiplier = new GlobalMultiplier(0.0, 0);
        globalTokenMultiplier = new GlobalMultiplier(0.0, 0);

        if (timeLeftSell > Time.nowMillis()) {
            globalSellMultiplier.setEndTime(timeLeftSell);
            globalSellMultiplier.setMultiplier(multiSell);
        }

        if (timeLeftTokens > Time.nowMillis()) {
            globalTokenMultiplier.setEndTime(timeLeftSell);
            globalTokenMultiplier.setMultiplier(multiTokens);
        }

        this.core.getLogger().info(String.format("Loaded Global Sell Multiplier %.2fx", multiSell));
        this.core.getLogger().info(String.format("Loaded Global Token Multiplier %.2fx", multiTokens));

    }

    private void saveGlobalMultipliers() {
        this.config.set("global-multiplier.sell.multiplier", this.globalSellMultiplier.getMultiplier());
        this.config.set("global-multiplier.sell.timeLeft", this.globalSellMultiplier.getEndTime());
        this.config.set("global-multiplier.tokens.multiplier", this.globalTokenMultiplier.getMultiplier());
        this.config.set("global-multiplier.tokens.timeLeft", this.globalTokenMultiplier.getEndTime());
        this.config.save();
        this.core.getLogger().info("Saved Global Multipliers into multipliers.yml");
    }

    private void loadSellMultiplier(Player player) {
        Schedulers.async().run(() -> {

            PlayerMultiplier multiplier = multipliersService.getSellMultiplier(player);

            if (multiplier == null) {
                return;
            }

            sellMultipliers.put(player.getUniqueId(), multiplier);

            core.debug(String.format("Loaded Sell Multiplier %.2fx for player %s", multiplier.getMultiplier(), player.getName()), this);
        });
    }

    private void loadTokenMultiplier(Player player) {
        Schedulers.async().run(() -> {

            PlayerMultiplier multiplier = multipliersService.getTokenMultiplier(player);

            if (multiplier == null) {
                return;
            }

            tokenMultipliers.put(player.getUniqueId(), multiplier);

            core.debug(String.format("Loaded Token Multiplier %.2fx for player %s", multiplier.getMultiplier(), player.getName()), this);
        });
    }

    private void removeExpiredMultipliers() {
        Schedulers.async().run(() -> {
            multipliersService.removeExpiredMultipliers();
            core.debug("Removed expired multipliers from DB.", this);
        });
    }


    @Override
    public void disable() {
        this.saveAllMultipliers();
        this.rankUpdateTask.stop();
        this.enabled = false;
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public boolean isHistoryEnabled() {
        return true;
    }

    @Override
    public void resetPlayerData() {
        this.multipliersRepository.clearTableData();
    }

    private void saveAllMultipliers() {
        Players.all().forEach(p -> {
            saveSellMultiplier(p, false);
            saveTokenMultiplier(p, false);
        });
        this.saveGlobalMultipliers();
        this.core.getLogger().info("Saved online players multipliers.");
    }


    private void loadMessages() {
        messages = new HashMap<>();
        for (String key : getConfig().get().getConfigurationSection("messages").getKeys(false)) {
            messages.put(key.toLowerCase(), TextUtils.applyColor(getConfig().get().getString("messages." + key)));
        }
    }

    private void registerCommands() {
        Commands.create()
                .assertPermission("xprison.multipliers.admin")
                .handler(c -> {
                    if (c.args().size() == 4) {
                        String type = c.rawArg(0);
                        double amount = c.arg(1).parseOrFail(Double.class);
                        int duration = c.arg(2).parseOrFail(Integer.class);
                        TimeUnit timeUnit;
                        try {
                            timeUnit = TimeUnit.valueOf(c.rawArg(3).toUpperCase());
                        } catch (IllegalArgumentException e) {
                            PlayerUtils.sendMessage(c.sender(), "&cInvalid time unit! Please use one from: " + StringUtils.join(TimeUnit.values(), ","));
                            return;
                        }

                        setupGlobalMultiplier(c.sender(), type, duration, timeUnit, amount);
                    } else if (c.args().size() == 2 && c.rawArg(1).equalsIgnoreCase("reset")) {
                        String type = c.rawArg(0);
                        resetGlobalMultiplier(c.sender(), type);
                    } else {
                        PlayerUtils.sendMessage(c.sender(), "&cInvalid usage!");
                        PlayerUtils.sendMessage(c.sender(), "&c/gmulti <money/token> <multiplier> <time> <time_unit>");
                        PlayerUtils.sendMessage(c.sender(), "&c/gmulti <money/token> reset");
                    }
                }).registerAndBind(core, "globalmultiplier", "gmulti");
        Commands.create()
                .assertPermission("xprison.multipliers.admin")
                .handler(c -> {
                    if (c.args().size() == 4) {
                        Player onlinePlayer = c.arg(0).parseOrFail(Player.class);
                        double amount = c.arg(1).parseOrFail(Double.class);
                        int duration = c.arg(2).parseOrFail(Integer.class);

                        TimeUnit timeUnit;
                        try {
                            timeUnit = TimeUnit.valueOf(c.rawArg(3).toUpperCase());
                        } catch (IllegalArgumentException e) {
                            PlayerUtils.sendMessage(c.sender(), "&cInvalid time unit! Please use one from: " + StringUtils.join(TimeUnit.values(), ","));
                            return;
                        }

                        setupSellMultiplier(c.sender(), onlinePlayer, amount, timeUnit, duration);
                    } else if (c.args().size() == 2 && c.rawArg(1).equalsIgnoreCase("reset")) {
                        Player onlinePlayer = Players.getNullable(c.rawArg(0));
                        resetSellMultiplier(c.sender(), onlinePlayer);
                    } else {
                        PlayerUtils.sendMessage(c.sender(), "&cInvalid usage!");
                        PlayerUtils.sendMessage(c.sender(), "&c/sellmulti <player> <multiplier> <time> <time_unit>");
                        PlayerUtils.sendMessage(c.sender(), "&c/sellmulti <player> reset");
                    }
                }).registerAndBind(core, "sellmulti", "sellmultiplier", "smulti");
        Commands.create()
                .assertPermission("xprison.multipliers.admin")
                .handler(c -> {
                    if (c.args().size() == 4) {
                        Player onlinePlayer = c.arg(0).parseOrFail(Player.class);
                        double amount = c.arg(1).parseOrFail(Double.class);
                        int duration = c.arg(2).parseOrFail(Integer.class);
                        TimeUnit timeUnit;
                        try {
                            timeUnit = TimeUnit.valueOf(c.rawArg(3).toUpperCase());
                        } catch (IllegalArgumentException e) {
                            PlayerUtils.sendMessage(c.sender(), "&cInvalid time unit! Please use one from: " + StringUtils.join(TimeUnit.values(), ","));
                            return;
                        }
                        setupTokenMultiplier(c.sender(), onlinePlayer, amount, timeUnit, duration);
                    } else if (c.args().size() == 2 && c.rawArg(1).equalsIgnoreCase("reset")) {
                        Player onlinePlayer = Players.getNullable(c.rawArg(0));
                        resetTokenMultiplier(c.sender(), onlinePlayer);
                    } else {
                        PlayerUtils.sendMessage(c.sender(), "&cInvalid usage!");
                        PlayerUtils.sendMessage(c.sender(), "&c/tokenmulti <player> <multiplier> <time> <time_unit>");
                        PlayerUtils.sendMessage(c.sender(), "&c/tokenmulti <player> reset");
                    }
                }).registerAndBind(core, "tokenmulti", "tokenmultiplier", "tmulti");
        Commands.create()
                .assertPlayer()
                .handler(c -> {

                    PlayerMultiplier sellMulti = getSellMultiplier(c.sender());
                    PlayerMultiplier tokenMulti = getTokenMultiplier(c.sender());
                    Multiplier rankMulti = getRankMultiplier(c.sender());

                    double sellMultiplier = sellMulti == null || !sellMulti.isValid() ? 0.0 : sellMulti.getMultiplier();
                    double tokenMultiplier = tokenMulti == null || !tokenMulti.isValid() ? 0.0 : tokenMulti.getMultiplier();
                    double rankMultipler = rankMulti == null ? 0.0 : rankMulti.getMultiplier();
                    String sellMultiplierDuration = sellMulti == null || sellMulti.isExpired() ? "" : sellMulti.getTimeLeftString();
                    String tokenMultiplierDuration = tokenMulti == null || tokenMulti.isExpired() ? "" : tokenMulti.getTimeLeftString();

                    PlayerUtils.sendMessage(c.sender(), messages.get("global_sell_multi").replace("%multiplier%", String.format("%,.2f", this.globalSellMultiplier.isValid() ? this.globalSellMultiplier.getMultiplier() : 0.0)).replace("%duration%", this.globalSellMultiplier.getTimeLeftString()));
                    PlayerUtils.sendMessage(c.sender(), messages.get("global_token_multi").replace("%multiplier%", String.format("%,.2f", this.globalTokenMultiplier.isValid() ? this.globalTokenMultiplier.getMultiplier() : 0.0)).replace("%duration%", this.globalTokenMultiplier.getTimeLeftString()));
                    PlayerUtils.sendMessage(c.sender(), messages.get("rank_multi").replace("%multiplier%", String.format("%,.2f", rankMultipler)));
                    PlayerUtils.sendMessage(c.sender(), messages.get("sell_multi").replace("%multiplier%", String.format("%,.2f", sellMultiplier)).replace("%duration%", sellMultiplierDuration));
                    PlayerUtils.sendMessage(c.sender(), messages.get("token_multi").replace("%multiplier%", String.format("%,.2f", tokenMultiplier)).replace("%duration%", tokenMultiplierDuration));
                }).registerAndBind(core, "multiplier", "multi");
    }

    private void resetGlobalMultiplier(CommandSender sender, String type) {
        switch (type.toLowerCase()) {
            case "sell":
            case "money":
                this.globalSellMultiplier.reset();
                PlayerUtils.sendMessage(sender, "&eGlobal Sell Multiplier &awas reset.");
                break;
            case "tokens":
            case "token":
                this.globalTokenMultiplier.reset();
                PlayerUtils.sendMessage(sender, "&eGlobal Token Multiplier &awas reset.");
                break;
        }
    }

    private void resetSellMultiplier(CommandSender sender, Player onlinePlayer) {
        if (onlinePlayer == null || !onlinePlayer.isOnline()) {
            PlayerUtils.sendMessage(sender, "&cPlayer must be online!");
            return;
        }

        if (sellMultipliers.containsKey(onlinePlayer.getUniqueId())) {
            sellMultipliers.remove(onlinePlayer.getUniqueId());
            PlayerUtils.sendMessage(sender, String.format("&aYou have reset &e%s's &eSell Multiplier.", onlinePlayer.getName()));
            PlayerUtils.sendMessage(onlinePlayer, messages.get("sell_multi_reset"));
        } else {
            PlayerUtils.sendMessage(sender, String.format("&cCould not fetch the &e%s's &eSell Multiplier.", onlinePlayer.getName()));
        }
    }

    private void resetTokenMultiplier(CommandSender sender, Player onlinePlayer) {
        if (onlinePlayer == null || !onlinePlayer.isOnline()) {
            PlayerUtils.sendMessage(sender, "&cPlayer must be online!");
            return;
        }

        if (this.tokenMultipliers.containsKey(onlinePlayer.getUniqueId())) {
            this.tokenMultipliers.remove(onlinePlayer.getUniqueId());
            PlayerUtils.sendMessage(sender, String.format("&aYou have reset &e%s's &eToken Multiplier.", onlinePlayer.getName()));
            PlayerUtils.sendMessage(onlinePlayer, messages.get("token_multi_reset"));
        } else {
            PlayerUtils.sendMessage(sender, String.format("&cCould not fetch the &e%s's &eToken Multiplier.", onlinePlayer.getName()));
        }
    }

    private void setupSellMultiplier(CommandSender sender, Player onlinePlayer, double amount, TimeUnit timeUnit, int duration) {
        if (onlinePlayer == null || !onlinePlayer.isOnline()) {
            PlayerUtils.sendMessage(sender, "&cPlayer must be online!");
            return;
        }

        callPlayerReceiveMultiplierEvent(onlinePlayer, amount, timeUnit, duration, MultiplierType.SELL);

        sellMultipliers.put(onlinePlayer.getUniqueId(), new PlayerMultiplier(onlinePlayer.getUniqueId(), amount, timeUnit, duration, MultiplierType.SELL));

        PlayerUtils.sendMessage(onlinePlayer, messages.get("sell_multi_apply").replace("%multiplier%", String.valueOf(amount)).replace("%time%", duration + " " + StringUtils.capitalize(timeUnit.name())));
        PlayerUtils.sendMessage(sender, String.format("&aYou have set &e%s's &eSell Multiplier &ato &e%.2f &afor &e%d &a%s.", onlinePlayer.getName(), amount, duration, StringUtils.capitalize(timeUnit.name())));
    }

    private PlayerMultiplierReceiveEvent callPlayerReceiveMultiplierEvent(Player onlinePlayer, double amount, TimeUnit timeUnit, int duration, MultiplierType type) {
        PlayerMultiplierReceiveEvent event = new PlayerMultiplierReceiveEvent(onlinePlayer, amount, timeUnit, duration, type);
        Events.call(event);
        return event;
    }

    private void setupTokenMultiplier(CommandSender sender, Player onlinePlayer, double amount, TimeUnit timeUnit, int duration) {
        if (onlinePlayer == null || !onlinePlayer.isOnline()) {
            PlayerUtils.sendMessage(sender, "&cPlayer must be online!");
            return;
        }

        callPlayerReceiveMultiplierEvent(onlinePlayer, amount, timeUnit, duration, MultiplierType.TOKENS);

        tokenMultipliers.put(onlinePlayer.getUniqueId(), new PlayerMultiplier(onlinePlayer.getUniqueId(), amount, timeUnit, duration, MultiplierType.TOKENS));

        PlayerUtils.sendMessage(onlinePlayer, messages.get("token_multi_apply").replace("%multiplier%", String.valueOf(amount)).replace("%time%", duration + " " + StringUtils.capitalize(timeUnit.name())));
        PlayerUtils.sendMessage(sender, String.format("&aYou have set &e%s's &eToken Multiplier &ato &e%.2f &afor &e%d &a%s.", onlinePlayer.getName(), amount, duration, StringUtils.capitalize(timeUnit.name())));
    }


    private void setupGlobalMultiplier(CommandSender sender, String type, int time, TimeUnit timeUnit, double amount) {
        double finalMulti;
        switch (type.toLowerCase()) {
            case "sell":
            case "money":
                finalMulti = globalSellMultiplier.getMultiplier() + amount > globalSellMultiMax ? globalSellMultiMax : this.globalSellMultiplier.getMultiplier() + amount;
                globalSellMultiplier.setMultiplier(finalMulti);
                globalSellMultiplier.setDuration(timeUnit, time);
                PlayerUtils.sendMessage(sender, String.format("&aYou have set the &eGlobal Sell Multiplier &ato &e%.2f &afor &e%d &a%s.", amount, time, StringUtils.capitalize(timeUnit.name())));
                break;
            case "tokens":
            case "token":
                finalMulti = globalTokenMultiplier.getMultiplier() + amount > globalTokenMultiMax ? globalTokenMultiMax : globalTokenMultiplier.getMultiplier() + amount;
                globalTokenMultiplier.setMultiplier(finalMulti);
                globalTokenMultiplier.setDuration(timeUnit, time);
                PlayerUtils.sendMessage(sender, String.format("&aYou have set the &eGlobal Token Multiplier &ato &e%.2f &afor &e%d &a%s.", amount, time, StringUtils.capitalize(timeUnit.name())));
                break;
        }
    }

    public GlobalMultiplier getGlobalSellMultiplier() {
        return this.globalSellMultiplier;
    }

    public GlobalMultiplier getGlobalTokenMultiplier() {
        return this.globalTokenMultiplier;
    }

    public PlayerMultiplier getSellMultiplier(Player p) {
        return sellMultipliers.get(p.getUniqueId());
    }

    public PlayerMultiplier getTokenMultiplier(Player p) {
        return tokenMultipliers.get(p.getUniqueId());
    }

    public Multiplier getRankMultiplier(Player p) {
        return rankMultipliers.get(p.getUniqueId());
    }

    private Multiplier calculateRankMultiplier(Player p) {
        PlayerMultiplier toReturn = new PlayerMultiplier(p.getUniqueId(), 0.0, 0, MultiplierType.SELL);

        for (String perm : permissionToMultiplier.keySet()) {
            if (p.hasPermission(perm)) {
                toReturn.addMultiplier(permissionToMultiplier.get(perm));
                break;
            }
        }

        return toReturn;
    }
}
