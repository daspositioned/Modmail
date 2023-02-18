package net.dasunterstrich.modmail.listener;

import net.dasunterstrich.modmail.modmail.ModmailManager;
import net.dasunterstrich.modmail.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.util.Collections;

public class SlashCommandListener extends ListenerAdapter {
    private final ModmailManager modmailManager;

    public SlashCommandListener(ModmailManager modmailManager) {
        this.modmailManager = modmailManager;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getFullCommandName().equals("contactuser")) {
            contactUser(event);
        } else if (event.getFullCommandName().equals("modmail")) {
            modmail(event);
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

        modmailManager.sendModmailMessage(event.getUser(), event.getOption("message").getAsString(), Collections.emptyList(), success -> {
            if (success) {
                event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Message sent successfully!", Color.GREEN)).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.buildEmbed("Failed to create modmail, try again later!", Color.RED)).queue();
            }
        });
    }
}
