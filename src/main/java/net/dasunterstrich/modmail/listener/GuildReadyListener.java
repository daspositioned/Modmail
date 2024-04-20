package net.dasunterstrich.modmail.listener;

import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class GuildReadyListener extends ListenerAdapter {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Set<Long> allowedGuildIds = Set.of(926228462379880538L, 497092213034188806L, 1079515429732614214L);

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        var guild = event.getGuild();
        var owner = guild.retrieveOwner().complete().getUser();
        logger.info("Guild ready: " + guild.getName() + " (" + guild.getId() + ") by " + owner.getName() + " (" + owner.getId() + ")");

        if (allowedGuildIds.contains(guild.getIdLong())) return;
        guild.leave().queue(unused -> logger.info("Guild left: " + guild.getName()));
    }
}
