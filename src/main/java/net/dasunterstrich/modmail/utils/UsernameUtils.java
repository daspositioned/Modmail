package net.dasunterstrich.modmail.utils;

import net.dv8tion.jda.api.entities.User;

public class UsernameUtils {
    public static String getUsername(User user) {
        var name = user.getGlobalName();
        if (name == null) {
            name = user.getAsTag();
        }

        return name;
    }
}
