package net.dasunterstrich.modmail.modmail;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class NotificationManager {
    private final Set<Long> subscribedUsers = new HashSet<>();

    public boolean optIn(long userID) {
        return subscribedUsers.add(userID);
    }

    public boolean optOut(long userID) {
        return subscribedUsers.remove(userID);
    }

    public String getStringRepresentation() {
        return subscribedUsers.stream()
                .map(l -> "<@" + l + ">")
                .collect(Collectors.joining(" "));
    }
}
