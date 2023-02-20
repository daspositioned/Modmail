package net.dasunterstrich.modmail.modmail;

import net.dasunterstrich.modmail.database.DatabaseHandler;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class BlocklistManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Set<Long> blockedUsers = new HashSet<>();
    private final DatabaseHandler databaseHandler;

    public BlocklistManager(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;

        try (var connection = databaseHandler.getConnection(); var statement = connection.createStatement()) {
            var resultSet = statement.executeQuery("SELECT * FROM modmail_blocklist");

            while (resultSet.next()) {
                blockedUsers.add(resultSet.getLong("user_id"));
            }
        } catch (SQLException exception) {
            logger.error("Could not load blocklist", exception);
            System.exit(-1);
        }
    }

    public Set<Long> getBlockedUsers() {
        return new HashSet<>(blockedUsers);
    }

    public boolean addUser(User user) {
        var success = blockedUsers.add(user.getIdLong());
        if (!success) return true;

        try (var connection = databaseHandler.getConnection()) {
            var statement = connection.prepareStatement("INSERT INTO modmail_blocklist (user_id) VALUES (?)");
            statement.setLong(1, user.getIdLong());
            statement.execute();
            statement.close();
        } catch (SQLException exception) {
            logger.error("Could not add user to blocklist", exception);
            return false;
        }

        return true;
    }

    public boolean removeUser(User user) {
        var success = blockedUsers.remove(user.getIdLong());
        if (!success) return true;

        try (var connection = databaseHandler.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DELETE FROM modmail_blocklist WHERE user_id = " + user.getIdLong());
        } catch (SQLException exception) {
            logger.error("Could not remove user from blocklist", exception);
            return false;
        }

        return true;
    }

    public boolean isBlocklisted(User user) {
        return blockedUsers.contains(user.getIdLong());
    }
}
