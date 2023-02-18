package net.dasunterstrich.modmail.utils;

import net.dv8tion.jda.api.entities.Message;

public class DiscordUtils {
    public static String getMessageLink(Message message) {
        return "https://discord.com/channels/" + message.getGuild().getId() + "/" + message.getChannel().getId() + "/" + message.getId();
    }
}
