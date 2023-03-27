package dev.drawethree.xprison.utils.misc;

import dev.drawethree.xprison.autosell.XPrisonAutoSell;
import dev.drawethree.xprison.autosell.model.SellRegion;
import org.bukkit.Location;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import org.codemc.worldguardwrapper.flag.IWrappedFlag;
import org.codemc.worldguardwrapper.region.IWrappedRegion;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public class RegionUtils {


	public static IWrappedRegion getRegionWithHighestPriority(@NotNull Location loc) {
		Set<IWrappedRegion> regions = WorldGuardWrapper.getInstance().getRegions(loc);
		SellRegion autoSellRegion = XPrisonAutoSell.getInstance().getManager().getAutoSellRegion(loc);

        if (autoSellRegion != null) {
            return autoSellRegion.getRegion();
        }

		IWrappedRegion lowestPriority = null;
		for (IWrappedRegion region : regions) {
			if (lowestPriority == null || region.getPriority() <= lowestPriority.getPriority()) {
				lowestPriority = region;
			}
		}

		return lowestPriority;
	}

	public static IWrappedRegion getRegionWithHighestPriorityAndFlag(Location loc, String flagName, Object flagValue) {
		Set<IWrappedRegion> regions = WorldGuardWrapper.getInstance().getRegions(loc);

		IWrappedRegion highestPrioRegion = null;

		for (IWrappedRegion region : regions) {
			for (Map.Entry<IWrappedFlag<?>, Object> flag : region.getFlags().entrySet()) {
				if (flag.getKey().getName().equalsIgnoreCase(flagName) && flag.getValue().equals(flagValue)) {
					if (highestPrioRegion == null || region.getPriority() > highestPrioRegion.getPriority()) {
						highestPrioRegion = region;
					}
				}
			}
		}
		return highestPrioRegion;
	}

	public static IWrappedRegion getFirstRegionAtLocation(Location loc) {
		Set<IWrappedRegion> regions = WorldGuardWrapper.getInstance().getRegions(loc);
		return regions.size() == 0 ? null : regions.iterator().next();
	}

	private RegionUtils() {
		throw new UnsupportedOperationException("Cannot instantiate");
	}
}
