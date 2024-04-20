package net.dasunterstrich.modmail.listener;

import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuildReadyListener extends ListenerAdapter {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        var guild = event.getGuild();
        String ownerName = guild.retrieveOwner().complete().getUser().getName();
        logger.info("Guild ready: " + guild.getName() + " (" + guild.getId() + ") by " + ownerName);
    }
}
