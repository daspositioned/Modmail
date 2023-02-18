package net.dasunterstrich.modmail.database;

import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

public class DatabaseHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private HikariDataSource dataSource;

    public void initializeDatabase() {
        initializeConnectionPool();
        initializeTables();
    }

    private void initializeConnectionPool() {
        dataSource = new HikariDataSource();
        dataSource.setDataSourceClassName("com.impossibl.postgres.jdbc.PGDataSource");
        dataSource.setUsername("modmail");
        dataSource.setPassword("");
        dataSource.setMinimumIdle(2);
        try {
            var connection = dataSource.getConnection();
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void initializeTables() {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS modmail_threads (user_id BIGINT PRIMARY KEY, thread_id BIGINT)");
            statement.execute("CREATE TABLE IF NOT EXISTS modmail_messages (id SERIAL PRIMARY KEY, user_id BIGINT, user_message BOOLEAN, content TEXT, timestamp BIGINT)");
        } catch (SQLException exception) {
            logger.error("Could not create tables", exception);
            System.exit(-1);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void addModmailMessage(User user, boolean isUserMessage, String content) {
        try (var connection = getConnection()) {
            var statement = connection.prepareStatement("INSERT INTO modmail_messages (user_id, user_message, content, timestamp) VALUES (?, ?, ?, ?)");
            statement.setLong(1, user.getIdLong());
            statement.setBoolean(2, isUserMessage);
            statement.setString(3, content);
            statement.setLong(4, Instant.now().getEpochSecond());

            statement.execute();
            statement.close();
        } catch (Exception exception) {
            logger.error("Could not add message to database", exception);
        }
    }

    public void closeDataSource() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
