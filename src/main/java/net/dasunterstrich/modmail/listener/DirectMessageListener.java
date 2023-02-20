package net.dasunterstrich.modmail.listener;

import io.github.cdimascio.dotenv.Dotenv;
import net.dasunterstrich.modmail.modmail.BlocklistManager;
import net.dasunterstrich.modmail.modmail.ModmailManager;
import net.dasunterstrich.modmail.utils.EmbedUtils;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;

public class DirectMessageListener extends ListenerAdapter {
    private final ModmailManager modmailManager;
    private final BlocklistManager blocklistManager;
    private final Dotenv config;

    public DirectMessageListener(ModmailManager modmailManager, BlocklistManager blocklistManager, Dotenv config) {
        this.modmailManager = modmailManager;
        this.blocklistManager = blocklistManager;
        this.config = config;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        if (event.isFromGuild() && !event.isWebhookMessage() && event.isFromThread()) {
            onGuildThreadMessage(event);
        } else if (!event.isFromGuild()) {
            onPrivateMessage(event);
        }
    }

    private void onGuildThreadMessage(MessageReceivedEvent event) {
        var threadChannel = (ThreadChannel) event.getChannel();
        if (!threadChannel.getParentChannel().getId().equals(config.get("FORUM_ID"))) return;

        var messageContent = event.getMessage().getContentRaw();
        if (!messageContent.startsWith("!")) return;
        messageContent = messageContent.substring(1);

        modmailManager.sendModmailResponse(threadChannel, messageContent, event.getMessage().getAttachments(), success -> {
            if (success) {
                event.getMessage().addReaction(Emoji.fromUnicode("U+2705")).queue();
            } else {
                event.getChannel().sendMessageEmbeds(
                        EmbedUtils.buildEmbed(
                                "An error occurred, please try again later!\n\nPlease note that files >8 MB can't be processed",
                                Color.RED)
                ).queue();
            }
        }, disabledDMs -> {
            event.getChannel().sendMessageEmbeds(EmbedUtils.buildEmbed("User disabled direct messages", Color.RED)).queue();
        });
    }

    private void onPrivateMessage(MessageReceivedEvent event) {
        var user = event.getAuthor();
        if (blocklistManager.isBlocklisted(user)) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.buildEmbed("You are blocked from submitting new modmails", Color.RED)).queue();
            return;
        }

        var messageContent = event.getMessage().getContentRaw();
        var messageAttachments = event.getMessage().getAttachments();
        modmailManager.sendModmailMessage(user, messageContent, messageAttachments, success -> {
            if (success) {
                event.getMessage().addReaction(Emoji.fromUnicode("U+2705")).queue();
            } else {
                event.getChannel().sendMessageEmbeds(
                        EmbedUtils.buildEmbed(
                                "An error occurred, please try again later!\n\nPlease note that we aren't able to receive files bigger than 8 MB",
                                Color.RED)
                ).queue();
            }
        });
    }
}
