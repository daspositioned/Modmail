package net.dasunterstrich.modmail.listener;

import io.github.cdimascio.dotenv.Dotenv;
import net.dasunterstrich.modmail.modmail.BlocklistManager;
import net.dasunterstrich.modmail.modmail.ModmailManager;
import net.dasunterstrich.modmail.utils.EmbedUtils;
import net.dasunterstrich.modmail.utils.UsernameUtils;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.sticker.Sticker;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class DirectMessageListener extends ListenerAdapter {
    private final Logger logger = LoggerFactory.getLogger(getClass());
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
            logger.info("Potential modmail from {}", UsernameUtils.getUsername(event.getAuthor()));
            onPrivateMessage(event);
        }
    }

    private void onGuildThreadMessage(MessageReceivedEvent event) {
        var threadChannel = (ThreadChannel) event.getChannel();
        if (!threadChannel.getParentChannel().getId().equals(config.get("FORUM_ID"))) return;

        var message = event.getMessage();
        var messageContent = message.getContentRaw();
        var attachments = message.getAttachments();
        var stickers = message.getStickers();

        if (!messageContent.startsWith("!")) return;
        messageContent = messageContent.substring(1);
        if (messageContent.isBlank() && attachments.isEmpty() && stickers.isEmpty()) return;
        messageContent = getMessageContentWithStickers(messageContent, stickers);

        modmailManager.sendModmailResponse(threadChannel, messageContent, attachments, success -> {
            if (success) {
                message.addReaction(Emoji.fromUnicode("U+2705")).queue();
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

        var message = event.getMessage();
        var messageContent = getMessageContentWithStickers(message.getContentRaw(), message.getStickers());
        var messageAttachments = message.getAttachments();

        modmailManager.sendModmailMessage(user, messageContent, messageAttachments, success -> {
            if (success) {
                message.addReaction(Emoji.fromUnicode("U+2705")).queue();
            } else {
                event.getChannel().sendMessageEmbeds(
                        EmbedUtils.buildEmbed(
                                "An error occurred, please try again later!\n\nPlease note that we aren't able to receive files bigger than 8 MB",
                                Color.RED)
                ).queue();
            }
        });
    }

    private String getMessageContentWithStickers(String previousContent, List<StickerItem> stickers) {
        String messageContent = previousContent;

        if (!stickers.isEmpty()) {
            var joinedStickerUrls = stickers.stream()
                    .map(Sticker::getIconUrl)
                    .collect(Collectors.joining("\n"));

            if (messageContent.isBlank()) {
                messageContent = joinedStickerUrls;
            } else {
                messageContent += "\n\n" + joinedStickerUrls;
            }
        }

        return messageContent;
    }
}
