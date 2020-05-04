package com.icrazyblaze.twitchmod.util;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class UptimeReader {

    public static String getUptimeString(String username) {

        try {
            return readStringFromURL("https://decapi.me/twitch/uptime?channel=" + username);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }

    public static String readStringFromURL(String requestURL) throws IOException {
        try (Scanner scanner = new Scanner(new URL(requestURL).openStream(),
                StandardCharsets.UTF_8.toString())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}
