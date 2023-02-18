package net.dasunterstrich.modmail.listener;

import net.dasunterstrich.modmail.modmail.ModmailManager;
import net.dasunterstrich.modmail.utils.EmbedUtils;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;

public class DirectMessageListener extends ListenerAdapter {
    private final ModmailManager modmailManager;

    public DirectMessageListener(ModmailManager modmailManager) {
        this.modmailManager = modmailManager;
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

    private void onGuildThreadMessage(MessageReceivedEvent event) { // TODO: Cooldown
        var threadChannel = (ThreadChannel) event.getChannel();
        if (threadChannel.getParentChannel().getIdLong() != 1076472547106889749L) return;

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
        });
    }

    private void onPrivateMessage(MessageReceivedEvent event) {
        var user = event.getAuthor();
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
