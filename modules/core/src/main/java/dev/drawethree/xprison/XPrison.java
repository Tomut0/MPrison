package dev.drawethree.xprison;

import com.github.lalyos.jfiglet.FigletFont;
import dev.drawethree.xprison.autominer.XPrisonAutoMiner;
import dev.drawethree.xprison.autosell.XPrisonAutoSell;
import dev.drawethree.xprison.config.FileManager;
import dev.drawethree.xprison.database.RedisDatabase;
import dev.drawethree.xprison.database.SQLDatabase;
import dev.drawethree.xprison.database.impl.MySQLDatabase;
import dev.drawethree.xprison.database.impl.SQLiteDatabase;
import dev.drawethree.xprison.database.model.ConnectionProperties;
import dev.drawethree.xprison.database.model.DatabaseCredentials;
import dev.drawethree.xprison.enchants.XPrisonEnchants;
import dev.drawethree.xprison.gangs.XPrisonGangs;
import dev.drawethree.xprison.gems.XPrisonGems;
import dev.drawethree.xprison.history.XPrisonHistory;
import dev.drawethree.xprison.mainmenu.MainMenu;
import dev.drawethree.xprison.mainmenu.help.HelpGui;
import dev.drawethree.xprison.mines.XPrisonMines;
import dev.drawethree.xprison.multipliers.XPrisonMultipliers;
import dev.drawethree.xprison.nicknames.repo.NicknameRepository;
import dev.drawethree.xprison.nicknames.repo.impl.NicknameRepositoryImpl;
import dev.drawethree.xprison.nicknames.service.NicknameService;
import dev.drawethree.xprison.nicknames.service.impl.NicknameServiceImpl;
import dev.drawethree.xprison.nms.NMSProvider;
import dev.drawethree.xprison.nms.factory.NMSProviderFactory;
import dev.drawethree.xprison.nms.factory.impl.NMSProviderFactoryImpl;
import dev.drawethree.xprison.pickaxelevels.XPrisonPickaxeLevels;
import dev.drawethree.xprison.placeholders.XPrisonMVdWPlaceholder;
import dev.drawethree.xprison.placeholders.XPrisonPAPIPlaceholder;
import dev.drawethree.xprison.prestiges.XPrisonPrestiges;
import dev.drawethree.xprison.ranks.XPrisonRanks;
import dev.drawethree.xprison.tokens.XPrisonTokens;
import dev.drawethree.xprison.utils.Constants;
import dev.drawethree.xprison.utils.compat.CompMaterial;
import dev.drawethree.xprison.utils.misc.SkullUtils;
import dev.drawethree.xprison.utils.text.TextUtils;
import lombok.Getter;
import me.jet315.prisonmines.JetsPrisonMines;
import me.jet315.prisonmines.JetsPrisonMinesAPI;
import me.lucko.helper.Commands;
import me.lucko.helper.Events;
import me.lucko.helper.plugin.ExtendedJavaPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import org.codemc.worldguardwrapper.flag.WrappedState;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.bukkit.Bukkit.getPluginManager;

@Getter
public final class XPrison extends ExtendedJavaPlugin {

    @Getter
    private static XPrison instance;

    private boolean debugMode;
    private Map<String, XPrisonModule> modules;
    private SQLDatabase pluginDatabase;
    private Economy economy;
    private FileManager fileManager;
    private XPrisonTokens tokens;
    private XPrisonGems gems;
    private XPrisonRanks ranks;
    private XPrisonPrestiges prestiges;
    private XPrisonMultipliers multipliers;
    private XPrisonEnchants enchants;
    private XPrisonAutoSell autoSell;
    private XPrisonAutoMiner autoMiner;
    private XPrisonPickaxeLevels pickaxeLevels;
    private XPrisonGangs gangs;
    private XPrisonMines mines;
    private XPrisonHistory history;

    private NMSProvider nmsProvider;

    private List<Material> supportedPickaxes;

    private JetsPrisonMinesAPI jetsPrisonMinesAPI;

    private NicknameService nicknameService;
    @Getter
    private RedisDatabase noSQLDatabase;


    @Override
    protected void load() {
        registerWGFlag();
    }

    @Override
    protected void enable() {
        instance = this;

        printOnEnableMessage();
        modules = new LinkedHashMap<>();

        fileManager = new FileManager(this);
        fileManager.getConfig("config.yml").copyDefaults(true).save();

        debugMode = getConfig().getBoolean("debug-mode", false);

        if (!loadNMSProvider()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!initDatabase()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupEconomy()) {
            getLogger().warning("Economy provider for Vault not found! Economy provider is strictly required. Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        } else {
            getLogger().info("Economy provider for Vault found - " + getEconomy().getName());
        }

        initVariables();
        initModules();
        loadModules();

        initNicknameService();

        registerPlaceholders();
        registerJetsPrisonMines();

        registerMainEvents();
        registerMainCommand();
        startMetrics();

        SkullUtils.init();
    }

    private void printOnEnableMessage() {
        try {
            getLogger().info(FigletFont.convertOneLine("X-PRISON"));
            getLogger().info(getDescription().getVersion());
            getLogger().info("By: " + getDescription().getAuthors());
            getLogger().info("Website: " + getDescription().getWebsite());
        } catch (IOException ignored) {
        }
    }

    private void initNicknameService() {
        NicknameRepository nicknameRepository = new NicknameRepositoryImpl(getPluginDatabase());
        nicknameRepository.createTables();
        nicknameService = new NicknameServiceImpl(nicknameRepository);
    }

    private void initVariables() {
        supportedPickaxes = getConfig().getStringList("supported-pickaxes").stream().
                map(CompMaterial::fromString).
                map(CompMaterial::getMaterial).
                collect(Collectors.toList());

        for (Material m : supportedPickaxes) {
            getLogger().info("Added support for pickaxe: " + m);
        }
    }

    private void loadModules() {
        if (getConfig().getBoolean("modules.tokens")) {
            loadModule(tokens);
        }

        if (getConfig().getBoolean("modules.gems")) {
            loadModule(gems);
        }

        if (getConfig().getBoolean("modules.ranks")) {
            loadModule(ranks);
        }

        if (getConfig().getBoolean("modules.prestiges")) {
            loadModule(prestiges);
        }

        if (getConfig().getBoolean("modules.multipliers")) {
            loadModule(multipliers);
        }

        if (getConfig().getBoolean("modules.autosell")) {
            if (isUltraBackpacksEnabled()) {
                getLogger().info("Module AutoSell will not be loaded because selling system is handled by UltraBackpacks.");
            } else {
                loadModule(autoSell);
            }
        }

        if (getConfig().getBoolean("modules.mines")) {
            loadModule(mines);
        }

        if (getConfig().getBoolean("modules.enchants")) {
            loadModule(enchants);
        }
        if (getConfig().getBoolean("modules.autominer")) {
            loadModule(autoMiner);
        }
        if (getConfig().getBoolean("modules.gangs")) {
            loadModule(gangs);
        }
        if (getConfig().getBoolean("modules.pickaxe_levels")) {
            if (!isModuleEnabled("Enchants")) {
                getLogger().warning(TextUtils.applyColor("&cX-Prison - Module 'Pickaxe Levels' requires to have enchants module enabled."));
            } else {
                loadModule(pickaxeLevels);
            }
        }
        if (getConfig().getBoolean("modules.history")) {
            loadModule(history);
        }
    }

    private boolean initDatabase() {
        try {
            String databaseType = this.getConfig().getString("database_type");
            ConnectionProperties connectionProperties = ConnectionProperties.fromConfig(this.getConfig());

            DatabaseCredentials redisCredentials = DatabaseCredentials.fromConfig("redis.", this.getConfig());
            noSQLDatabase = new RedisDatabase(redisCredentials);
            getLogger().info(String.format("Using Redis (%s) mechanism.", redisCredentials.getHost()));

            if ("sqlite".equalsIgnoreCase(databaseType)) {
                pluginDatabase = new SQLiteDatabase(this, connectionProperties);
                getLogger().info("Using SQLite (local) database.");
            } else if ("mysql".equalsIgnoreCase(databaseType)) {
                DatabaseCredentials credentials = DatabaseCredentials.fromConfig("mysql.", this.getConfig());
                pluginDatabase = new MySQLDatabase(this, credentials, connectionProperties);
                getLogger().info("Using MySQL (remote) database.");
            } else {
                getLogger().warning(String.format("Error! Unknown database type: %s. Disabling plugin.", databaseType));
                getServer().getPluginManager().disablePlugin(this);
                return false;
            }

            pluginDatabase.connect();
        } catch (Exception e) {
            getLogger().warning("Could not maintain Database Connection. Disabling plugin.");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void initModules() {
        tokens = new XPrisonTokens(this);
        gems = new XPrisonGems(this);
        ranks = new XPrisonRanks(this);
        prestiges = new XPrisonPrestiges(this);
        multipliers = new XPrisonMultipliers(this);
        enchants = new XPrisonEnchants(this);
        autoSell = new XPrisonAutoSell(this);
        autoMiner = new XPrisonAutoMiner(this);
        pickaxeLevels = new XPrisonPickaxeLevels(this);
        gangs = new XPrisonGangs(this);
        mines = new XPrisonMines(this);
        history = new XPrisonHistory(this);

        modules.put(tokens.getName().toLowerCase(), tokens);
        modules.put(gems.getName().toLowerCase(), gems);
        modules.put(ranks.getName().toLowerCase(), ranks);
        modules.put(prestiges.getName().toLowerCase(), prestiges);
        modules.put(multipliers.getName().toLowerCase(), multipliers);
        modules.put(enchants.getName().toLowerCase(), enchants);
        modules.put(autoSell.getName().toLowerCase(), autoSell);
        modules.put(autoMiner.getName().toLowerCase(), autoMiner);
        modules.put(pickaxeLevels.getName().toLowerCase(), pickaxeLevels);
        modules.put(gangs.getName().toLowerCase(), gangs);
        modules.put(mines.getName().toLowerCase(), mines);
        modules.put(history.getName().toLowerCase(), history);
    }

    private void registerMainEvents() {
        //Updating of mapping table
        Events.subscribe(PlayerJoinEvent.class, EventPriority.LOW)
                .handler(e -> nicknameService.updatePlayerNickname(e.getPlayer())).bindWith(this);
    }

    private void startMetrics() {
        new Metrics(this, Constants.METRICS_SERVICE_ID);
    }

    private void loadModule(XPrisonModule module) {
        if (module.isEnabled()) {
            return;
        }
        module.enable();
        getLogger().info(TextUtils.applyColor(String.format("&aX-Prison - Module %s loaded.", module.getName())));
    }

    //Always unload via iterator!
    private void unloadModule(XPrisonModule module) {
        if (!module.isEnabled()) {
            return;
        }
        module.disable();
        this.getLogger().info(TextUtils.applyColor(String.format("&aX-Prison - Module %s unloaded.", module.getName())));
    }

    public void debug(String msg, XPrisonModule module) {
        if (!debugMode) {
            return;
        }
        if (module != null) {
            getLogger().info(String.format("[%s] %s", module.getName(), TextUtils.applyColor(msg)));
        } else {
            getLogger().info(TextUtils.applyColor(msg));
        }
    }

    public void reloadModule(XPrisonModule module) {
        if (!module.isEnabled()) {
            return;
        }
        module.reload();
        getLogger().info(TextUtils.applyColor(String.format("X-Prison - Module %s reloaded.", module.getName())));
    }

    private void registerMainCommand() {

        List<String> commandAliases = getConfig().getStringList("main-command-aliases");
        String[] commandAliasesArray = commandAliases.toArray(new String[commandAliases.size()]);

        Commands.create()
                .assertPermission("xprison.admin")
                .handler(c -> {
                    if (c.args().size() == 0 && c.sender() instanceof Player) {
                        new MainMenu(this, (Player) c.sender()).open();
                    } else if (c.args().size() == 1 && c.sender() instanceof Player && "help".equalsIgnoreCase(c.rawArg(0)) || "?".equalsIgnoreCase(c.rawArg(0))) {
                        new HelpGui((Player) c.sender()).open();
                    }
                }).registerAndBind(this, commandAliasesArray);
    }

    @Override
    protected void disable() {

        Iterator<XPrisonModule> it = modules.values().iterator();

        while (it.hasNext()) {
            unloadModule(it.next());
            it.remove();
        }

        noSQLDatabase.close();
    }


    public boolean isModuleEnabled(String moduleName) {
        XPrisonModule module = this.modules.get(moduleName.toLowerCase());
        return module != null && module.isEnabled();
    }

    private void registerPlaceholders() {

        if (isMVdWPlaceholderAPIEnabled()) {
            new XPrisonMVdWPlaceholder(this).register();
        }

        if (isPlaceholderAPIEnabled()) {
            new XPrisonPAPIPlaceholder(this).register();
        }
    }

    private void registerJetsPrisonMines() {
        if (getPluginManager().getPlugin("JetsPrisonMines") != null) {
            this.jetsPrisonMinesAPI = ((JetsPrisonMines) getServer().getPluginManager().getPlugin("JetsPrisonMines")).getAPI();
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return true;
    }

    public boolean isPickaxeSupported(Material m) {
        return supportedPickaxes.contains(m);
    }

    public boolean isPickaxeSupported(ItemStack item) {
        return item != null && isPickaxeSupported(item.getType());
    }

    private boolean loadNMSProvider() {
        NMSProviderFactory factory = new NMSProviderFactoryImpl();
        try {
            nmsProvider = factory.createNMSProvider();
            getLogger().info("NMSProvider loaded:  " + nmsProvider.getClass().getSimpleName());
        } catch (ClassNotFoundException e) {
            getLogger().warning("NMSProvider could not find a valid implementation for this server version.");
        } catch (InstantiationException | IllegalAccessException | ClassCastException ex) {
            ex.printStackTrace();
        }

        return nmsProvider != null;
    }

    public Collection<XPrisonModule> getModules() {
        return modules.values();
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean enabled) {
        debugMode = enabled;
        getConfig().set("debug-mode", debugMode);
        saveConfig();
    }

    public boolean isUltraBackpacksEnabled() {
        return getServer().getPluginManager().isPluginEnabled("UltraBackpacks");
    }

    public boolean isPlaceholderAPIEnabled() {
        return getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public boolean isMVdWPlaceholderAPIEnabled() {
        return getServer().getPluginManager().isPluginEnabled("MVdWPlaceholderAPI");
    }

    private void registerWGFlag() {

        if (getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }

        try {
            getWorldGuardWrapper().registerFlag(Constants.ENCHANTS_WG_FLAG_NAME, WrappedState.class, WrappedState.DENY);
        } catch (IllegalStateException e) {
            // This happens during plugin reloads. Flag cannot be registered as WG was already loaded,
            // so we can safely ignore this exception.
        }
    }

    public WorldGuardWrapper getWorldGuardWrapper() {
        return WorldGuardWrapper.getInstance();
    }
}
