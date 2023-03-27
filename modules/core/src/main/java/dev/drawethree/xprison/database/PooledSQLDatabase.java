package dev.drawethree.xprison.database;

import com.zaxxer.hikari.HikariDataSource;
import dev.drawethree.xprison.XPrison;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class PooledSQLDatabase extends SQLDatabase {

	protected static final AtomicInteger POOL_COUNTER = new AtomicInteger(0);
	protected HikariDataSource hikari;

	public PooledSQLDatabase(XPrison plugin) {
		super(plugin);
	}

	@Override
	public void close() {
		if (hikari != null) {
			hikari.close();
			plugin.getLogger().info("Database Connection closed");
		}
	}

	@Override
	public Connection getConnection() {
		try {
			return hikari.getConnection();
		} catch (SQLException ex) {
			plugin.getLogger().warning("Unable to get database connection!");
			ex.printStackTrace();
		}
		return null;
	}


}
