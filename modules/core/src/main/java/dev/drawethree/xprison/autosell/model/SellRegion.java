package dev.drawethree.xprison.autosell.model;

import dev.drawethree.xprison.utils.compat.CompMaterial;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.codemc.worldguardwrapper.region.IWrappedRegion;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@ToString
public class SellRegion {

    @Getter
    private final IWrappedRegion region;
    @Getter
    private final World world;

    @Getter
    private final String permissionRequired;
    private final Map<CompMaterial, Double> sellPrices;


    public SellRegion(IWrappedRegion region, World world) {
        this.region = region;
        this.world = world;
        permissionRequired = null;
        sellPrices = new HashMap<>();
    }

    public double getSellPriceForMaterial(CompMaterial material) {
        return sellPrices.getOrDefault(material, 0.0);
    }

    public Set<CompMaterial> getSellingMaterials() {
        return sellPrices.keySet();
    }

    public Set<CompMaterial> getSellingMaterialsSorted(Comparator<CompMaterial> comparator) {
        return sellPrices.keySet().stream().sorted(comparator).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void addSellPrice(CompMaterial material, double price) {
        sellPrices.put(material, price);
    }

    public boolean contains(Location loc) {
        return Objects.equals(loc.getWorld(), world) && region.contains(loc);
    }

    public boolean canPlayerSellInRegion(Player player) {
        if (permissionRequired == null || permissionRequired.isEmpty()) {
            return true;
        }
        return player.hasPermission(permissionRequired);
    }

    public double getPriceForItem(ItemStack item) {
        CompMaterial material = CompMaterial.fromItem(item);
        return item.getAmount() * sellPrices.getOrDefault(material, 0.0);
    }

    public Map<AutoSellItemStack, Double> previewInventorySell(Player player) {
        return previewItemsSell(Arrays.asList(player.getInventory().getContents()));
    }

    public Map<AutoSellItemStack, Double> previewItemsSell(Collection<ItemStack> items) {

        Map<AutoSellItemStack, Double> itemsToSell = new HashMap<>();

        for (ItemStack item : items) {

            if (item == null) {
                continue;
            }

            double priceForItem = getPriceForItem(item);

            if (priceForItem <= 0.0) {
                continue;
            }

            itemsToSell.put(new AutoSellItemStack(item), priceForItem);
        }

        return itemsToSell;
    }

    public boolean sellsMaterial(CompMaterial material) {
        return sellPrices.containsKey(material);
    }

    public void removeSellPrice(CompMaterial material) {
        sellPrices.remove(material);
    }
}
