package net.dasunterstrich.modmail.listener;

import net.dasunterstrich.modmail.modmail.ModmailManager;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ChannelUpdateArchivedListener extends ListenerAdapter {
    private final ModmailManager modmailManager;

    public ChannelUpdateArchivedListener(ModmailManager modmailManager) {
        this.modmailManager = modmailManager;
    }

    @Override
    public void onChannelUpdateArchived(ChannelUpdateArchivedEvent event) {
        if (!event.isFromType(ChannelType.GUILD_PUBLIC_THREAD)) return;

        var threadChannel = event.getChannel().asThreadChannel();
        if (!modmailManager.isModmailThread(threadChannel)) return;

        if (Boolean.TRUE.equals(event.getNewValue())) {
            return;
        }

        setTag(threadChannel, "open");
    }

    private void setTag(ThreadChannel threadChannel, String tagName) {
        var tag = threadChannel.getParentChannel().asForumChannel().getAvailableTagsByName(tagName, true).get(0);
        threadChannel.getManager().setAppliedTags(tag).queue();
    }
}
