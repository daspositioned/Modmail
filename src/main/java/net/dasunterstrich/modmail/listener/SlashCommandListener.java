package net.dasunterstrich.modmail.listener;

import io.github.cdimascio.dotenv.Dotenv;
import net.dasunterstrich.modmail.modmail.BlocklistManager;
import net.dasunterstrich.modmail.modmail.ModmailManager;
import net.dasunterstrich.modmail.modmail.NotificationManager;
import net.dasunterstrich.modmail.utils.EmbedUtils;
import net.dasunterstrich.modmail.utils.UsernameUtils;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.concurrent.Executors;

public class SlashCommandListener extends ListenerAdapter {
    private final ModmailManager modmailManager;
    private final BlocklistManager blocklistManager;
    private final NotificationManager notificationManager;
    private final Dotenv config;
    private static final Logger logger = LoggerFactory.getLogger(SlashCommandListener.class);

    public SlashCommandListener(ModmailManager modmailManager, BlocklistManager blocklistManager, NotificationManager notificationManager, Dotenv config) {
        this.modmailManager = modmailManager;
        this.blocklistManager = blocklistManager;
        this.notificationManager = notificationManager;
        this.config = config;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || !event.getGuild().getId().equals(config.get("GUILD_ID"))) {
            return;
        }

        if (event.getFullCommandName().equals("contactuser")) {
            contactUser(event);
        } else if (event.getFullCommandName().startsWith("blocklist")) {
            blocklist(event);
        } else if (event.getFullCommandName().equals("close")) {
            close(event);
        } else if (event.getFullCommandName().startsWith("notifications")) {
            notifications(event);
        }
    }

    private void contactUser(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        try {
            var guild = event.getGuild();
            var threadID = modmailManager.getModmailThread(event.getOption("user").getAsUser());
            var forumChannel = guild.getForumChannelById(config.get("FORUM_ID"));
            var modmailThread = event.getGuild().getThreadChannelById(threadID);

            if (modmailThread == null) {
                forumChannel.retrieveArchivedPublicThreadChannels().forEachAsync(threadChannel -> {
                    if (threadChannel.getIdLong() != threadID) {
                        return true;
                    }

                    sendModmailThreadMessage(event, threadChannel);
                    return false;
                });
            } else {
                sendModmailThreadMessage(event, modmailThread);
            }
        } catch (Exception exception) {
            event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Failed to create thread", Color.RED)).queue();
            logger.error("Could not contact user", exception);
        }
    }

    private void sendModmailThreadMessage(SlashCommandInteractionEvent event, ThreadChannel threadChannel) {
        threadChannel.getManager().setArchived(false).queue(success -> threadChannel.sendMessage(event.getUser().getAsMention()).queue(message -> event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Modmail thread: \n<#" + threadChannel.getId() + ">", Color.PINK)).queue()));
    }

    private void blocklist(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        switch (event.getFullCommandName().split(" ")[1]) {
            case "add" -> {
                var user = event.getOption("user").getAsUser();
                var success = blocklistManager.addUser(user);
                if (success) {
                    event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Blocked user " + UsernameUtils.getUsername(user), Color.PINK)).queue();
                } else {
                    event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Internal error", Color.RED)).queue();
                }
            }

            case "list" -> Executors.newSingleThreadExecutor().submit(() -> {
                var blockedUsers = blocklistManager.getBlockedUsers().stream()
                        .map(userID -> event.getJDA().retrieveUserById(userID).mapToResult().complete())
                        .filter(result -> !result.isFailure())
                        .map(Result::get)
                        .map(user -> UsernameUtils.getUsername(user) + " (" + user.getId() + ")")
                        .toList();

                if (blockedUsers.isEmpty()) {
                    event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("There are no users on the blocklist", Color.PINK)).queue();
                } else {
                    event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Blocklist", String.join("\n", blockedUsers), Color.PINK)).queue();
                }
            });

            case "remove" -> {
                var user = event.getOption("user").getAsUser();
                var success = blocklistManager.removeUser(user);
                if (success) {
                    event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Unblocked user " + UsernameUtils.getUsername(user), Color.PINK)).queue();
                } else {
                    event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Internal error", Color.RED)).queue();
                }
            }
        }
    }

    private void close(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD) {
            event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Invalid channel", Color.RED)).queue();
            return;
        }

        var threadChannel = event.getChannel().asThreadChannel();
        if (modmailManager.isModmailThread(threadChannel)) {
            event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Modmail closed", Color.PINK)).queue(success -> {
                var tag = threadChannel.getParentChannel().asForumChannel().getAvailableTagsByName("closed", true).get(0);
                threadChannel.getManager().setAppliedTags(tag).queue(s -> event.getChannel().asThreadChannel().getManager().setArchived(true).queue());
            });
        } else {
            event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("I cannot close threads which are not modmails", Color.RED)).queue();
        }
    }

    private void notifications(SlashCommandInteractionEvent event) {
        if (event.getFullCommandName().split(" ")[1].equalsIgnoreCase("enable")) {
            var success = notificationManager.optIn(event.getUser().getIdLong());
            if (success) {
                event.replyEmbeds(EmbedUtils.buildEmbed("You enabled modmail notifications for yourself", Color.PINK)).setEphemeral(true).queue();
            } else {
                event.replyEmbeds(EmbedUtils.buildEmbed("You already have modmail notifications enabled", Color.RED)).setEphemeral(true).queue();
            }
        } else {
            var success = notificationManager.optOut(event.getUser().getIdLong());
            if (success) {
                event.replyEmbeds(EmbedUtils.buildEmbed("You disabled modmail notifications for yourself", Color.PINK)).setEphemeral(true).queue();
            } else {
                event.replyEmbeds(EmbedUtils.buildEmbed("You already have modmail notifications disabled", Color.RED)).setEphemeral(true).queue();
            }
        }
    }
 }
