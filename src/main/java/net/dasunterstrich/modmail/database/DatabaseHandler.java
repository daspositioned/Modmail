package net.dasunterstrich.modmail.database;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private HikariDataSource dataSource;

    public void initializeDatabase(String address, String database, String username, String password) {
        initializeConnectionPool(address, database, username, password);
        initializeTables();
    }

    private void initializeConnectionPool(String address, String database, String username, String password) {
        dataSource = new HikariDataSource();
        dataSource.setDataSourceClassName("com.impossibl.postgres.jdbc.PGDataSource");
        dataSource.addDataSourceProperty("serverName", address);
        dataSource.addDataSourceProperty("databaseName", database);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMinimumIdle(2);
        try {
            var connection = dataSource.getConnection();
            connection.close();
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void initializeTables() {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS modmail_threads (user_id BIGINT PRIMARY KEY, thread_id BIGINT)");
            statement.execute("CREATE TABLE IF NOT EXISTS modmail_blocklist (user_id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE IF NOT EXISTS modmail_notifications (user_id BIGINT PRIMARY KEY)");
        } catch (SQLException exception) {
            logger.error("Could not create tables", exception);
            System.exit(-1);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void closeDataSource() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
