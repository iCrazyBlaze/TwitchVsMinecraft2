package io.github.icrazyblaze.twitchmod.bots.irc;

import io.github.icrazyblaze.twitchmod.CommandHandlers;
import io.github.icrazyblaze.twitchmod.config.BotConfig;
import io.github.icrazyblaze.twitchmod.config.ConfigManager;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.cap.EnableCapHandler;

/**
 * This class is responsible for connecting the IRC bot, which is defined by the TwitchBot class.
 * The tryConnect method will either connect the bot or reconnect if it is already connected.
 */

public class TwitchConnectionHelper {

    public static PircBotX bot = null;
    private static Thread botThread = null;

    public static void login() {

        // Update settings before connecting
        ConfigManager.updateFromConfig();

        if (BotConfig.TWITCH_KEY.isEmpty()) {
            CommandHandlers.broadcastMessage(new StringTextComponent(TextFormatting.RED + "No OAuth key provided. Use /ttv key to set the key."));
            return;
        }

        if (isConnected()) {

            // Disconnect before connecting again
            disconnectBot();
            CommandHandlers.broadcastMessage(new StringTextComponent(TextFormatting.DARK_PURPLE + "Reconnecting..."));

        } else {
            CommandHandlers.broadcastMessage(new StringTextComponent(TextFormatting.DARK_PURPLE + String.format("Connecting to channel %s...", BotConfig.CHANNEL_NAME)));
        }

        try {

            Configuration config = new Configuration.Builder()
                    .setAutoReconnect(true)
                    .setAutoNickChange(false) // Twitch doesn't support multiple users
                    .setOnJoinWhoEnabled(false) // Twitch doesn't support WHO command
                    .setCapEnabled(true)
                    .addCapHandler(new EnableCapHandler("twitch.tv/membership"))
                    .addCapHandler(new EnableCapHandler("twitch.tv/tags"))
                    .setName("MinecraftBot")
                    .addServer("irc.twitch.tv", 6667)
                    .setServerPassword(BotConfig.TWITCH_KEY)
                    .addAutoJoinChannel("#" + BotConfig.CHANNEL_NAME)
                    .addListener(new TwitchBot())
                    .buildConfiguration();

            bot = new PircBotX(config);

            botThread = new Thread(() -> {

                try {
                    bot.startBot();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            });

            botThread.start();

        } catch (Exception e) {
            e.printStackTrace();
            CommandHandlers.broadcastMessage(new StringTextComponent(TextFormatting.RED + "Could not connect: " + e));
        }
    }

    public static boolean isConnected() {

        if (bot != null) {
            return bot.isConnected();
        } else {
            return false;
        }

    }

    public static void disconnectBot() {

        bot.stopBotReconnect();
        bot.close();
        botThread.interrupt();

    }

}