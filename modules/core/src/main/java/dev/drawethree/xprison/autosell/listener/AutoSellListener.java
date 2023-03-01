package dev.drawethree.xprison.autosell.listener;

import dev.drawethree.xprison.autosell.XPrisonAutoSell;
import dev.drawethree.xprison.autosell.model.SellRegion;
import dev.drawethree.xprison.utils.compat.CompMaterial;
import dev.drawethree.xprison.utils.player.PlayerUtils;
import me.lucko.helper.Events;
import me.lucko.helper.Schedulers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.TextColor.color;

public class AutoSellListener {

    private final XPrisonAutoSell plugin;

    public AutoSellListener(XPrisonAutoSell plugin) {
        this.plugin = plugin;
    }

    public void subscribeToEvents() {
        subscribeToPlayerJoinEvent();
        subscribeToBlockBreakEvent();
        subscribeToWorldLoadEvent();
    }

    private void subscribeToWorldLoadEvent() {
        Events.subscribe(WorldLoadEvent.class)
                .handler(e -> plugin.getManager().loadPostponedAutoSellRegions(e.getWorld())).bindWith(plugin.getCore());
    }

    private void subscribeToPlayerJoinEvent() {
        Events.subscribe(PlayerJoinEvent.class)
                .handler(e -> Schedulers.sync().runLater(() -> {

                    if (plugin.getManager().hasAutoSellEnabled(e.getPlayer())) {
                        PlayerUtils.sendMessage(e.getPlayer(), plugin.getAutoSellConfig().getMessage("autosell_enable"));
                        return;
                    }

                    if (plugin.getManager().canPlayerEnableAutosellOnJoin(e.getPlayer())) {
                        plugin.getManager().toggleAutoSell(e.getPlayer());
                    }
                }, 20)).bindWith(plugin.getCore());
    }

    private void subscribeToBlockBreakEvent() {
        Events.subscribe(BlockBreakEvent.class, EventPriority.HIGHEST)
                .filter(e -> !e.isCancelled() && plugin.getManager().getAutoSellRegion(e.getBlock().getLocation()) != null)
                .handler(e -> {
                    Player player = e.getPlayer();
                    Block block = e.getBlock();
                    Material itemInMainHand = player.getInventory().getItemInMainHand().getType();

                    if (block.getType().name().endsWith("LEAVES") && !player.isOp() && itemInMainHand != Material.SHEARS) {
                        player.sendMessage(text("Возьмите в руки в ножницы!", color(255, 0, 0)));
                        e.setCancelled(true);
                        return;
                    }

                    boolean success = false;

                    if (plugin.getManager().hasAutoSellEnabled(player)) {
                        success = plugin.getManager().autoSellBlock(player, block);
                    }

                    if (!success) {
                        success = plugin.getManager().givePlayerItem(player, block);
                    }

                    if (success) {
                        block.setType(CompMaterial.AIR.toMaterial());
                    } else {
                        e.setCancelled(true);
                    }
                }).bindWith(plugin.getCore());
    }
}

