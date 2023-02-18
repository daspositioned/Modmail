package net.dasunterstrich.modmail;

import io.github.cdimascio.dotenv.Dotenv;

public class Main {
    public static void main(String[] args) {
        var env = Dotenv.load();
        new Bot().start(env);
    }
}
