package net.dasunterstrich.modmail.listener;

import net.dasunterstrich.modmail.modmail.BlocklistManager;
import net.dasunterstrich.modmail.modmail.ModmailManager;
import net.dasunterstrich.modmail.utils.EmbedUtils;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.Result;

import java.awt.*;
import java.util.Collections;
import java.util.concurrent.Executors;

public class SlashCommandListener extends ListenerAdapter {
    private final ModmailManager modmailManager;
    private final BlocklistManager blocklistManager;

    public SlashCommandListener(ModmailManager modmailManager, BlocklistManager blocklistManager) {
        this.modmailManager = modmailManager;
        this.blocklistManager = blocklistManager;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getFullCommandName().equals("contactuser")) {
            contactUser(event);
        } else if (event.getFullCommandName().equals("modmail")) {
            modmail(event);
        } else if (event.getFullCommandName().startsWith("blocklist")) {
            blocklist(event);
        } else if (event.getFullCommandName().equals("close")) {
            close(event);
        }
    }

    private void contactUser(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        try {
            var threadID = modmailManager.getModmailThread(event.getOption("user").getAsUser());
            event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Modmail thread: \n<#" + threadID + ">", Color.GREEN)).queue();
        } catch (Exception exception) {
            event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Failed to create thread", Color.RED)).queue();
        }
    }

    private void modmail(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (blocklistManager.isBlocklisted(event.getUser())) {
            event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("You are blocked from submitting new modmails", Color.RED)).queue();
            return;
        }

        modmailManager.sendModmailMessage(event.getUser(), event.getOption("message").getAsString(), Collections.emptyList(), success -> {
            if (success) {
                event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Message sent successfully!", Color.GREEN)).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Failed to create modmail, try again later!", Color.RED)).queue();
            }
        });
    }

    private void blocklist(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        switch (event.getFullCommandName().split(" ")[1]) {
            case "add" -> {
                var user = event.getOption("user").getAsUser();
                var success = blocklistManager.addUser(user);
                if (success) {
                    event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Blocked user " + user.getAsTag(), Color.GREEN)).queue();
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
                        event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("There are no users on the blocklist", Color.GREEN)).queue();
                    } else {
                        event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Blocklist", String.join("\n", blockedUsers), Color.GREEN)).queue();
                    }
                });
            }
            case "remove" -> {
                var user = event.getOption("user").getAsUser();
                var success = blocklistManager.removeUser(user);
                if (success) {
                    event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Unblocked user " + user.getAsTag(), Color.GREEN)).queue();
                } else {
                    event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Internal error", Color.RED)).queue();
                }
            }
        }
    }

    private void close(SlashCommandInteractionEvent event) {
        var threadChannel = event.getChannel().asThreadChannel();

        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD) return;
        if (modmailManager.isModmailThread(threadChannel)) {
            event.replyEmbeds(EmbedUtils.buildEmbed("Modmail closed", Color.GREEN)).queue(success -> {
                var tag = threadChannel.getParentChannel().asForumChannel().getAvailableTagsByName("closed", true).get(0);
                threadChannel.getManager().setAppliedTags(tag).queue(s -> event.getChannel().asThreadChannel().getManager().setArchived(true).queue());
            });
        } else {
            event.replyEmbeds(EmbedUtils.buildEmbed("I cannot close threads which are not modmails", Color.RED)).setEphemeral(true).queue();
        }
    }
}
