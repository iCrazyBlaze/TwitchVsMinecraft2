package io.github.icrazyblaze.twitchmod.config;

import io.github.icrazyblaze.twitchmod.util.PlayerHelper;

import java.util.List;

public class BotConfig {

    public static String TWITCH_KEY = null;
    public static String DISCORD_TOKEN = null;
    public static String CHANNEL_NAME = null;
    public static List<? extends String> DISCORD_CHANNELS;
    public static boolean showChatMessages = false;
    public static boolean showCommandsInChat = false;
    public static String prefix = "!";

    private static String username = null;

    public static String getUsername() {

        try {
            if (username.isEmpty()) {
                username = PlayerHelper.getDefaultPlayer().getName().getString();
            }

            return username;

        } catch (Exception e) {
            return null;
        }

    }

    public static void setUsername(String newname) {
        username = newname;
    }

}