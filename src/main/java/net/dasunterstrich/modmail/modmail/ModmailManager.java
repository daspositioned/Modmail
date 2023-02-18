package net.dasunterstrich.modmail.modmail;

import io.github.cdimascio.dotenv.Dotenv;
import net.dasunterstrich.modmail.database.DatabaseHandler;
import net.dasunterstrich.modmail.utils.AttachmentSender;
import net.dasunterstrich.modmail.utils.DiscordUtils;
import net.dasunterstrich.modmail.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class ModmailManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final DatabaseHandler databaseHandler;
    private final Dotenv config;
    private final HashMap<Long, Long> modmailThreads = new HashMap<>();

    public ModmailManager(DatabaseHandler databaseHandler, Dotenv config) {
        this.databaseHandler = databaseHandler;
        this.config = config;

        try (var connection = databaseHandler.getConnection(); var statement = connection.createStatement()) {
            var resultSet = statement.executeQuery("SELECT * FROM modmail_threads");

            while (resultSet.next()) {
                var userID = resultSet.getLong("user_id");
                var threadID = resultSet.getLong("thread_id");
                modmailThreads.put(userID, threadID);
            }
        } catch (SQLException exception) {
            logger.error("Could not initialize modmail threads", exception);
        }
    }

    public void sendModmailMessage(User user, String content, List<Message.Attachment> attachments, Consumer<Boolean> success) {
        var jda = user.getJDA();

        try {
            var modmailThreadID = getModmailThread(user);
            var modmailThread = jda.getGuildById(config.get("GUILD_ID")).getThreadChannelById(modmailThreadID);
            if (modmailThread == null) throw new IllegalStateException();

            if (!content.isEmpty()) {
                modmailThread.sendMessageEmbeds(EmbedUtils.buildEmbed(content, Color.GREEN)).queue(message -> {
                    var modmailNotificationChannel = modmailThread.getGuild().getTextChannelById(config.get("NOTIFICATION_CHANNEL_ID"));
                    var embed = new EmbedBuilder()
                            .setTitle("New Message from " + user.getAsTag(), DiscordUtils.getMessageLink(message))
                            .setTimestamp(Instant.now())
                            .build();
                    modmailNotificationChannel.sendMessageEmbeds(embed).queue(null, failure -> success.accept(false));
                }, failure -> success.accept(false));
            }

            databaseHandler.addModmailMessage(user, true, content);

            if (attachments.isEmpty()) {
                success.accept(true);
            } else {
                AttachmentSender.sendAttachment(modmailThread, attachments, success, v -> {
                    modmailThread.sendMessageEmbeds(EmbedUtils.buildEmbed("User tried to send at least one big file, unable to process it", Color.RED)).queue();
                });
            }
        } catch (Exception exception) {
            logger.error("Exception occurred", exception);
            success.accept(false);
        }
    }

    public long getModmailThread(User user) throws SQLException {
        if (!modmailThreads.containsKey(user.getIdLong())) createModmailThread(user);

        return modmailThreads.get(user.getIdLong());
    }

    private void createModmailThread(User user) throws SQLException {
        var jda = user.getJDA();
        var threadID = jda.getGuildById(config.get("GUILD_ID"))
                .getForumChannelById(config.get("FORUM_ID"))
                .createForumPost(user.getAsTag() + " (" + user.getId() + ")", MessageCreateData.fromContent("New modmail"))
                .complete()
                .getThreadChannel()
                .getIdLong();

        try (var connection = databaseHandler.getConnection()) {
            var statement = connection.prepareStatement("INSERT INTO modmail_threads (user_id, thread_id) VALUES (?, ?)");
            statement.setLong(1, user.getIdLong());
            statement.setLong(2, threadID);

            statement.execute();
            statement.close();
        }

        modmailThreads.put(user.getIdLong(), threadID);
    }

    public void sendModmailResponse(ThreadChannel threadChannel, String messageContent, List<Message.Attachment> attachments, Consumer<Boolean> success) {
        var jda = threadChannel.getJDA();

        try {
            var modmailEntry = modmailThreads.entrySet().stream()
                    .filter(entry -> entry.getValue() == threadChannel.getIdLong())
                    .findAny();

            if (modmailEntry.isEmpty()) {
                success.accept(false);
                logger.error("Modmail entry empty");
                return;
            }

            var userID = modmailEntry.get().getKey();
            jda.retrieveUserById(userID)
                    .flatMap(User::openPrivateChannel)
                    .queue(channel -> {
                        channel.sendMessageEmbeds(EmbedUtils.buildEmbed("New Message from the Bocchicord Moderation Team", messageContent, Color.GREEN)).queue();

                        databaseHandler.addModmailMessage(channel.getUser(), false, messageContent);

                        if (attachments.isEmpty()) {
                            success.accept(true);
                        } else {
                            AttachmentSender.sendAttachment(channel, attachments, success, v -> {
                                threadChannel.sendMessageEmbeds(EmbedUtils.buildEmbed("Attachment bigger than 8 MB, upload failed", Color.RED)).queue();                            });
                        }
                    });
        } catch (Exception exception) {
            logger.error("Exception occurred", exception);
            success.accept(false);
        }
    }
}
