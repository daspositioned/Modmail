package net.dasunterstrich.modmail.listener;

import io.github.cdimascio.dotenv.Dotenv;
import net.dasunterstrich.modmail.modmail.BlocklistManager;
import net.dasunterstrich.modmail.modmail.ModmailManager;
import net.dasunterstrich.modmail.utils.EmbedUtils;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.Result;

import java.awt.*;
import java.util.concurrent.Executors;

public class SlashCommandListener extends ListenerAdapter {
    private final ModmailManager modmailManager;
    private final BlocklistManager blocklistManager;
    private final Dotenv config;

    public SlashCommandListener(ModmailManager modmailManager, BlocklistManager blocklistManager, Dotenv config) {
        this.modmailManager = modmailManager;
        this.blocklistManager = blocklistManager;
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
            exception.printStackTrace();
        }
    }

    private void sendModmailThreadMessage(SlashCommandInteractionEvent event, ThreadChannel threadChannel) {
        threadChannel.getManager().setArchived(false).queue(success -> {
            threadChannel.sendMessage(event.getUser().getAsMention()).queue(message -> {
                event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Modmail thread: \n<#" + threadChannel.getId() + ">", Color.PINK)).queue();
            });
        });
    }

    private void blocklist(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        switch (event.getFullCommandName().split(" ")[1]) {
            case "add" -> {
                var user = event.getOption("user").getAsUser();
                var success = blocklistManager.addUser(user);
                if (success) {
                    event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Blocked user " + user.getAsTag(), Color.PINK)).queue();
                } else {
                    event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Internal error", Color.RED)).queue();
                }
            }
            case "list" -> {
                Executors.newSingleThreadExecutor().submit(() -> {
                    var blockedUsers = blocklistManager.getBlockedUsers().stream()
                            .map(userID -> event.getJDA().retrieveUserById(userID).mapToResult().complete())
                            .filter(result -> !result.isFailure())
                            .map(Result::get)
                            .map(user -> user.getAsTag() + " (" + user.getId() + ")")
                            .toList();

                    if (blockedUsers.isEmpty()) {
                        event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("There are no users on the blocklist", Color.PINK)).queue();
                    } else {
                        event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Blocklist", String.join("\n", blockedUsers), Color.PINK)).queue();
                    }
                });
            }
            case "remove" -> {
                var user = event.getOption("user").getAsUser();
                var success = blocklistManager.removeUser(user);
                if (success) {
                    event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Unblocked user " + user.getAsTag(), Color.PINK)).queue();
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
}
