package net.dasunterstrich.modmail.listener;

import net.dasunterstrich.modmail.modmail.ModmailManager;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ThreadDeletionListener extends ListenerAdapter {
    private final ModmailManager modmailManager;

    public ThreadDeletionListener(ModmailManager modmailManager) {
        this.modmailManager = modmailManager;
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (!event.isFromGuild()) return;
        if (event.getChannel().getType() != ChannelType.GUILD_PUBLIC_THREAD) return;

        var threadChannel = event.getChannel().asThreadChannel();
        modmailManager.deleteModmailThread(threadChannel);
    }
}
