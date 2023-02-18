package net.dasunterstrich.modmail.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.*;
import java.time.Instant;

public class EmbedUtils {
    public static MessageEmbed buildEmbed(String content, Color color) {
        return new EmbedBuilder()
                .setDescription(content)
                .setColor(color)
                .setTimestamp(Instant.now())
                .build();
    }

    public static MessageEmbed buildEmbed(String title, String content, Color color) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(content)
                .setColor(color)
                .setTimestamp(Instant.now())
                .build();
    }
}
