package net.dasunterstrich.modmail.utils;

import net.dv8tion.jda.api.entities.User;

public class UsernameUtils {
    public static String getUsername(User user) {
        if (user.getGlobalName() == null) {
            return user.getAsTag();
        }

        return user.getName();
    }
}
