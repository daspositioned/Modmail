package net.dasunterstrich.modmail;

import net.dasunterstrich.modmail.database.DatabaseHandler;
import net.dasunterstrich.modmail.listener.DirectMessageListener;
import net.dasunterstrich.modmail.listener.SlashCommandListener;
import net.dasunterstrich.modmail.modmail.ModmailManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Bot {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void start() {
        var databaseHandler = initializeDatabase();
        var modmailManager = new ModmailManager(databaseHandler);

        JDA jda = JDABuilder.createDefault(readToken())
                .setActivity(Activity.playing("with Bocchicord"))
                .addEventListeners(new DirectMessageListener(modmailManager), new SlashCommandListener(modmailManager))
                .setMemberCachePolicy(MemberCachePolicy.ONLINE)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .build();

        //jda.updateCommands().addCommands(commandManager.registeredCommandData()).queue();
        jda.updateCommands().addCommands(
                Commands.slash("contactuser", "Opens a modmail thread for this user")
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                    .addOption(OptionType.USER, "user", "The user to contact", true),
                Commands.slash("modmail", "Send a modmail to the staff team")
                        .addOption(OptionType.STRING, "message", "The message to send", true)).queue();
        logger.info("Commands initialized");
    }

    private String readToken() {
        try {
            return Files.readAllLines(Path.of("token.txt")).get(0);
        } catch (IOException e) {
            logger.error("Token not found, please create a token.txt");
            throw new RuntimeException(e);
        }
    }

    private DatabaseHandler initializeDatabase() {
        var databaseHandler = new DatabaseHandler();
        databaseHandler.initializeDatabase();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                databaseHandler.closeDataSource();
                logger.info("Database connection shutdown!");
            }
        });

        logger.info("Database connection established!");
        return databaseHandler;
    }
}
