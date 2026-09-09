package net.dasunterstrich.modmail.modmail;

import io.github.cdimascio.dotenv.Dotenv;
import net.dasunterstrich.modmail.database.DatabaseHandler;
import net.dasunterstrich.modmail.utils.AttachmentSender;
import net.dasunterstrich.modmail.utils.DiscordUtils;
import net.dasunterstrich.modmail.utils.EmbedUtils;
import net.dasunterstrich.modmail.utils.UsernameUtils;
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
    private final BlocklistManager blocklistManager;
    private final NotificationManager notificationManager;
    private final Dotenv config;
    private final HashMap<Long, Long> modmailThreads = new HashMap<>();

    public ModmailManager(DatabaseHandler databaseHandler, BlocklistManager blocklistManager, NotificationManager notificationManager, Dotenv config) {
        this.databaseHandler = databaseHandler;
        this.blocklistManager = blocklistManager;
        this.notificationManager = notificationManager;
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
        var guild = jda.getGuildById(config.get("GUILD_ID"));
        var forumChannel = guild.getForumChannelById(config.get("FORUM_ID"));

        try {
            var modmailThreadID = getModmailThread(user);
            var modmailThread = guild.getThreadChannelById(modmailThreadID);

            if (modmailThread == null) {
                forumChannel.retrieveArchivedPublicThreadChannels().forEachAsync(threadChannel -> {
                    if (threadChannel.getIdLong() != modmailThreadID) return true;

                    sendModmailMessage(user, threadChannel, content, attachments, success);
                    return false;
                }, throwable -> {
                    logger.error("Could not reuse thread", throwable);
                    success.accept(false);
                }).join();
            } else {
                sendModmailMessage(user, modmailThread, content, attachments, success);
            }
        } catch (Exception exception) {
            logger.error("Exception occurred", exception);
            success.accept(false);
        }
    }

    private void sendModmailMessage(User user, ThreadChannel modmailThread, String content, List<Message.Attachment> attachments, Consumer<Boolean> success) {
        if (modmailThread == null) throw new IllegalStateException();

        var openTag = modmailThread.getParentChannel().asForumChannel().getAvailableTagsByName("open", true);

        if (modmailThread.isArchived() || modmailThread.getAppliedTags().stream().noneMatch(tag -> tag.getName().equalsIgnoreCase("open"))) {
            modmailThread.getManager().setArchived(false).queue(s -> modmailThread.getManager().setAppliedTags(openTag).queue(t -> {
                var notificationText = notificationManager.getStringRepresentation();
                if (notificationText.isBlank()) return;

                modmailThread.sendMessage(notificationText).queue(null, throwable -> success.accept(false));
            }, throwable -> success.accept(false)));
        }

        if (!content.isEmpty()) {
            modmailThread.sendMessageEmbeds(EmbedUtils.buildEmbed(content, Color.PINK)).queue(message -> {
                var modmailNotificationChannel = modmailThread.getGuild().getTextChannelById(config.get("NOTIFICATION_CHANNEL_ID"));
                var embed = new EmbedBuilder()
                        .setTitle("New Message from " + UsernameUtils.getUsername(user), DiscordUtils.getMessageLink(message))
                        .setTimestamp(Instant.now())
                        .setColor(Color.PINK)
                        .build();
                modmailNotificationChannel.sendMessageEmbeds(embed).queue(null, failure -> success.accept(false));
            }, failure -> success.accept(false));
        }

        if (attachments.isEmpty()) {
            success.accept(true);
        } else {
            AttachmentSender.sendAttachment(modmailThread, attachments, success, error -> modmailThread.sendMessageEmbeds(EmbedUtils.buildEmbed("User tried to send at least one big file, unable to process it", Color.RED)).queue(secondSuccess -> success.accept(true), failure -> success.accept(false)));
        }

        if (!modmailThread.getName().equals(getForumTitle(user))) {
            var oldName = modmailThread.getName();
            modmailThread.getManager().setName(getForumTitle(user)).queue();
            modmailThread.sendMessageEmbeds(EmbedUtils.buildEmbed("User changed username", "Old name: " + oldName, Color.LIGHT_GRAY)).queue();
        }
    }

    public long getModmailThread(User user) throws SQLException {
        if (!hasModmailThread(user)) createModmailThread(user);

        return modmailThreads.get(user.getIdLong());
    }

    public boolean hasModmailThread(User user) {
        return modmailThreads.containsKey(user.getIdLong());
    }

    public boolean isModmailThread(ThreadChannel threadChannel) {
        return modmailThreads.containsValue(threadChannel.getIdLong());
    }

    public void deleteModmailThread(ThreadChannel threadChannel) {
        var threadID = threadChannel.getIdLong();
        if (!modmailThreads.containsValue(threadID)) return;

        var modmailEntry = modmailThreads.entrySet().stream().filter(entry -> entry.getValue() == threadID).findAny();
        if (modmailEntry.isEmpty()) return;

        var userID = modmailEntry.get().getKey();
        try (var connection = databaseHandler.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DELETE FROM modmail_threads WHERE thread_id = " + threadID);
            logger.info("Deleted thread " + threadChannel.getName());
        } catch (SQLException exception) {
            logger.error("Could not delete thread " + threadChannel.getName(), exception);
        }

        modmailThreads.remove(userID);
    }

    private void createModmailThread(User user) throws SQLException {
        var jda = user.getJDA();
        var threadID = jda.getGuildById(config.get("GUILD_ID"))
                .getForumChannelById(config.get("FORUM_ID"))
                .createForumPost(getForumTitle(user), MessageCreateData.fromContent("New modmail"))
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

    private String getForumTitle(User user) {
        return UsernameUtils.getUsername(user) + " (" + user.getId() + ")";
    }

    public void sendModmailResponse(ThreadChannel threadChannel, String messageContent, List<Message.Attachment> attachments, Consumer<Boolean> success, Consumer<Void> disabledDMs) {
        var jda = threadChannel.getJDA();

        var openTag = threadChannel.getParentChannel().asForumChannel().getAvailableTagsByName("open", true);
        threadChannel.getManager().setAppliedTags(openTag).queue();

        try {
            var modmailEntry = modmailThreads.entrySet().stream()
                    .filter(entry -> entry.getValue() == threadChannel.getIdLong())
                    .findAny();

            if (modmailEntry.isEmpty()) {
                success.accept(false);
                logger.error("Modmail entry empty + " + threadChannel.getIdLong());
                return;
            }

            var userID = modmailEntry.get().getKey();
            jda.retrieveUserById(userID)
                    .flatMap(User::openPrivateChannel)
                    .queue(channel -> {
                        if (blocklistManager.isBlocklisted(channel.getUser())) {
                            threadChannel.sendMessageEmbeds(EmbedUtils.buildEmbed("Warning: User is on blocklist and can't reply", Color.YELLOW)).queue();
                        }

                        var guildName = threadChannel.getGuild().getName();
                        channel.sendMessageEmbeds(EmbedUtils.buildEmbed("New Message from the " + guildName + " Moderation Team", messageContent, Color.PINK)).queue(messageSuccess -> {
                            if (attachments.isEmpty()) {
                                success.accept(true);
                            } else {
                                AttachmentSender.sendAttachment(channel, attachments, success, v -> threadChannel.sendMessageEmbeds(EmbedUtils.buildEmbed("Attachment bigger than 8 MB, upload failed", Color.RED)).queue());
                            }
                        }, throwable -> {
                            logger.warn("User has DMs closed");
                            disabledDMs.accept(null);
                            success.accept(false);
                        });
                    });
        } catch (Exception exception) {
            logger.error("Exception occurred", exception);
            success.accept(false);
        }
    }
}
