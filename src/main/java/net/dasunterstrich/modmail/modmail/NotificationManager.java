package net.dasunterstrich.modmail.modmail;

import net.dasunterstrich.modmail.database.DatabaseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class NotificationManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Set<Long> subscribedUsers;
    private final DatabaseHandler databaseHandler;
    private final ExecutorService executor;

    public NotificationManager(DatabaseHandler databaseHandler) {
        this.subscribedUsers = new HashSet<>();
        this.databaseHandler = databaseHandler;
        this.executor = Executors.newSingleThreadExecutor();

        loadMessages();
    }

    private void loadMessages() {
        try (var connection = databaseHandler.getConnection()) {
            try (var statement = connection.prepareStatement("SELECT * from modmail_notifications")) {
                try (var resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        var userId = resultSet.getLong("user_id");
                        subscribedUsers.add(userId);
                        logger.info("Loaded notifications for {}", userId);
                    }
                }
            }
        } catch (SQLException exception) {
            logger.error("Failed to load notifications", exception);
        }
    }

    public boolean optIn(long userID) {
        var success = subscribedUsers.add(userID);

        if (success) {
            executor.submit(() -> {
                try (var connection = databaseHandler.getConnection()) {
                    try (var statement = connection.prepareStatement("INSERT INTO modmail_notifications VALUES (?)")) {
                        statement.setLong(1, userID);
                        statement.execute();
                    }
                } catch (SQLException exception) {
                    logger.error("Failed to save notification update", exception);
                }
            });
        }

        return success;
    }

    public boolean optOut(long userID) {
        var success = subscribedUsers.remove(userID);

        if (success) {
            executor.submit(() -> {
                try (var connection = databaseHandler.getConnection()) {
                    try (var statement = connection.prepareStatement("DELETE FROM modmail_notifications WHERE user_id = ?")) {
                        statement.setLong(1, userID);
                        statement.execute();
                    }
                } catch (SQLException exception) {
                    logger.error("Failed to save notification update", exception);
                }
            });
        }

        return success;
    }

    public String getStringRepresentation() {
        return subscribedUsers.stream()
                .map(l -> "<@" + l + ">")
                .collect(Collectors.joining(" "));
    }
}
