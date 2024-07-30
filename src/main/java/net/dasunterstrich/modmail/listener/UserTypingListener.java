package net.dasunterstrich.modmail.listener;

import io.github.cdimascio.dotenv.Dotenv;
import net.dasunterstrich.modmail.modmail.ModmailManager;
import net.dv8tion.jda.api.events.user.UserTypingEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.sql.SQLException;

public class UserTypingListener extends ListenerAdapter {
    private final ModmailManager modmailManager;
    private final Dotenv config;

    public UserTypingListener(ModmailManager modmailManager, Dotenv config) {
        this.modmailManager = modmailManager;
        this.config = config;
    }

    @Override
    public void onUserTyping(UserTypingEvent event) {
        var user = event.getUser();
        if (user.isBot()) return;
        if (!modmailManager.hasModmailThread(user)) return;

        long modmailThreadID = 0;
        try {
            modmailThreadID = modmailManager.getModmailThread(event.getUser());
        } catch (SQLException e) {
            // Ignore this, can't happen
        }
        var modmailThread = event.getJDA().getGuildById(config.get("GUILD_ID")).getThreadChannelById(modmailThreadID);
        if (modmailThread == null) return;

        modmailThread.sendTyping().queue();
    }
}
