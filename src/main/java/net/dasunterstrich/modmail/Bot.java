package net.dasunterstrich.modmail;

import io.github.cdimascio.dotenv.Dotenv;
import net.dasunterstrich.modmail.database.DatabaseHandler;
import net.dasunterstrich.modmail.listener.ChannelUpdateArchivedListener;
import net.dasunterstrich.modmail.listener.DirectMessageListener;
import net.dasunterstrich.modmail.listener.SlashCommandListener;
import net.dasunterstrich.modmail.listener.ThreadDeletionListener;
import net.dasunterstrich.modmail.modmail.BlocklistManager;
import net.dasunterstrich.modmail.modmail.ModmailManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Bot {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void start(Dotenv config) {
        var databaseHandler = initializeDatabase(config);
        var blocklistManager = new BlocklistManager(databaseHandler);
        var modmailManager = new ModmailManager(databaseHandler, blocklistManager, config);

        JDA jda = JDABuilder.createDefault(readToken())
                .setActivity(Activity.playing("with Bocchicord"))
                .addEventListeners(
                        new DirectMessageListener(modmailManager, blocklistManager, config),
                        new SlashCommandListener(modmailManager, blocklistManager, config),
                        new ThreadDeletionListener(modmailManager),
                        new ChannelUpdateArchivedListener(modmailManager))
                .setMemberCachePolicy(MemberCachePolicy.ONLINE)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .build();

        jda.updateCommands().addCommands(
                Commands.slash("contactuser", "Open a modmail thread for a user")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                        .addOption(OptionType.USER, "user", "The user to contact", true),
                Commands.slash("close", "Close a modmail thread")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)),
                Commands.slash("blocklist", "Manage the modmail blocklist")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                        .addSubcommands(
                                new SubcommandData("add", "Add a user to the blocklist")
                                        .addOption(OptionType.USER, "user", "The user to block", true),
                                new SubcommandData("list", "Display the current blocklist"),
                                new SubcommandData("remove", "Remove a user from the blocklist")
                                        .addOption(OptionType.USER, "user", "The user to unblock", true)
                        ))
                .queue();
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

    private DatabaseHandler initializeDatabase(Dotenv config) {
        var databaseHandler = new DatabaseHandler();
        databaseHandler.initializeDatabase(config.get("POSTGRES_USER"), config.get("POSTGRES_PASSWORD"));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            databaseHandler.closeDataSource();
            logger.info("Database connection shutdown!");
        }));

        logger.info("Database connection established!");
        return databaseHandler;
    }
}
