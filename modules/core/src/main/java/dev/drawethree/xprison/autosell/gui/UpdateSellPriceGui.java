package dev.drawethree.xprison.autosell.gui;

import dev.drawethree.xprison.autosell.XPrisonAutoSell;
import dev.drawethree.xprison.autosell.model.SellRegion;
import dev.drawethree.xprison.utils.compat.CompMaterial;
import dev.drawethree.xprison.utils.item.ItemStackBuilder;
import me.lucko.helper.menu.Gui;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class UpdateSellPriceGui extends Gui {

	private final SellRegion sellRegion;
	private final CompMaterial material;
	private double price;

	public UpdateSellPriceGui(Player player, SellRegion sellRegion, CompMaterial material) {
		super(player, 5, "Editing Sell Price");
		this.sellRegion = sellRegion;
		this.material = material;
		price = sellRegion.getSellPriceForMaterial(material);
	}

	@Override
	public void redraw() {
		setPreviewItem();
		setActionItems();
		setBackItem();
		setSaveItem();
	}

	private void setPreviewItem() {
		setItem(4, ItemStackBuilder.of(this.material.toItem()).name("&eSell Price").lore(" ", "&7Selling price for this block", String.format("&7in region &b%s &7is &2$&a%,.2f", this.sellRegion.getRegion().getId(), this.price)).buildItem().build());
	}

	private void setSaveItem() {
		this.setItem(40, ItemStackBuilder.of(CompMaterial.GREEN_WOOL.toItem()).name("&aSave").lore("&7Click to save the current price.").build(() -> {
			saveChanges();
			close();
			new SellRegionGui(sellRegion, getPlayer()).open();
		}));
	}

	private void setBackItem() {
		setItem(36, ItemStackBuilder.of(Material.ARROW).name("&cBack").lore("&7Click to go back to all blocks.").build(() -> {
			close();
			new SellRegionGui(sellRegion, getPlayer()).open();
		}));
	}

	private void setActionItems() {
		setItem(10, ItemStackBuilder.of(CompMaterial.GREEN_STAINED_GLASS_PANE.toItem()).name("&a+$1.0").build(() -> handleAddition(1.0)));
		setItem(11, ItemStackBuilder.of(CompMaterial.GREEN_STAINED_GLASS_PANE.toItem()).name("&a+$5.0").build(() -> handleAddition(5.0)));
		setItem(12, ItemStackBuilder.of(CompMaterial.GREEN_STAINED_GLASS_PANE.toItem()).name("&a+$10.0").build(() -> handleAddition(10.0)));
		setItem(19, ItemStackBuilder.of(CompMaterial.GREEN_STAINED_GLASS_PANE.toItem()).name("&a+$25.0").build(() -> handleAddition(25.0)));
		setItem(20, ItemStackBuilder.of(CompMaterial.GREEN_STAINED_GLASS_PANE.toItem()).name("&a+$50.0").build(() -> handleAddition(50.0)));
		setItem(21, ItemStackBuilder.of(CompMaterial.GREEN_STAINED_GLASS_PANE.toItem()).name("&a+$100.0").build(() -> handleAddition(100.0)));
		setItem(28, ItemStackBuilder.of(CompMaterial.GREEN_STAINED_GLASS_PANE.toItem()).name("&a+$250.0").build(() -> handleAddition(250.0)));
		setItem(29, ItemStackBuilder.of(CompMaterial.GREEN_STAINED_GLASS_PANE.toItem()).name("&a+$500.0").build(() -> handleAddition(500.0)));
		setItem(30, ItemStackBuilder.of(CompMaterial.GREEN_STAINED_GLASS_PANE.toItem()).name("&a+$1000.0").build(() -> handleAddition(1000.0)));

		setItem(14, ItemStackBuilder.of(CompMaterial.RED_STAINED_GLASS_PANE.toItem()).name("&c-$1.0").build(() -> handleAddition(-1.0)));
		setItem(15, ItemStackBuilder.of(CompMaterial.RED_STAINED_GLASS_PANE.toItem()).name("&c-$5.0").build(() -> handleAddition(-5.0)));
		setItem(16, ItemStackBuilder.of(CompMaterial.RED_STAINED_GLASS_PANE.toItem()).name("&c-$10.0").build(() -> handleAddition(-10.0)));
		setItem(23, ItemStackBuilder.of(CompMaterial.RED_STAINED_GLASS_PANE.toItem()).name("&c-$25.0").build(() -> handleAddition(-25.0)));
		setItem(24, ItemStackBuilder.of(CompMaterial.RED_STAINED_GLASS_PANE.toItem()).name("&c-$50.0").build(() -> handleAddition(-50.0)));
		setItem(25, ItemStackBuilder.of(CompMaterial.RED_STAINED_GLASS_PANE.toItem()).name("&c-$100.0").build(() -> handleAddition(-100.0)));
		setItem(32, ItemStackBuilder.of(CompMaterial.RED_STAINED_GLASS_PANE.toItem()).name("&c-$250.0").build(() -> handleAddition(-250.0)));
		setItem(33, ItemStackBuilder.of(CompMaterial.RED_STAINED_GLASS_PANE.toItem()).name("&c-$500.0").build(() -> handleAddition(-500.0)));
		setItem(34, ItemStackBuilder.of(CompMaterial.RED_STAINED_GLASS_PANE.toItem()).name("&c-$1000.0").build(() -> handleAddition(-1000.0)));
	}

	private void saveChanges() {
		sellRegion.addSellPrice(material, price);
		XPrisonAutoSell.getInstance().getAutoSellConfig().saveSellRegion(sellRegion);
	}

	private void handleAddition(double addition) {
		if (price + addition < 0.0) {
			price = 0.0;
		} else {
			price += addition;
		}
		redraw();
	}
}
