package dev.drawethree.xprison.multipliers.multiplier;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.ChatColor;

import java.util.concurrent.TimeUnit;

@Getter
public abstract class Multiplier {

	protected double multiplier;

	@Setter
	protected long endTime;

	Multiplier(double multiplier, TimeUnit timeUnit, long duration) {
		this.multiplier = multiplier;
		endTime = System.currentTimeMillis() + timeUnit.toMillis(duration);
	}

	Multiplier(double multiplier, long endTime) {
		this.multiplier = multiplier;
		this.endTime = endTime;
	}

	public String getTimeLeftString() {
		if (System.currentTimeMillis() > endTime) {
			return "";
		}

		long timeLeft = endTime - System.currentTimeMillis();

		long days = timeLeft / (24 * 60 * 60 * 1000);
		timeLeft -= days * (24 * 60 * 60 * 1000);

		long hours = timeLeft / (60 * 60 * 1000);
		timeLeft -= hours * (60 * 60 * 1000);

		long minutes = timeLeft / (60 * 1000);
		timeLeft -= minutes * (60 * 1000);

		long seconds = timeLeft / (1000);

		timeLeft -= seconds * 1000;

		return ChatColor.GRAY + "(" + ChatColor.WHITE + days + "d " + hours + "h " + minutes + "m " + seconds + "s" + ChatColor.GRAY + ")";
	}

	public void setMultiplier(double amount) {
		multiplier = amount;
	}

	public void addMultiplier(double amount) {
		multiplier += amount;
	}

	public void setDuration(TimeUnit unit, int duration) {
		if (endTime == 0) {
			endTime = System.currentTimeMillis();
		}

		endTime = unit.toMillis(duration);
	}

	public boolean isExpired() {
		return System.currentTimeMillis() > endTime;
	}

	public boolean isValid() {
		return !isExpired() && multiplier > 0.0;
	}

	public void reset() {
		this.multiplier = 0.0;
		this.endTime = 0;
	}

}
