package io.github.icrazyblaze.twitchmod.chat;

import io.github.icrazyblaze.twitchmod.CommandHandlers;
import io.github.icrazyblaze.twitchmod.Main;
import io.github.icrazyblaze.twitchmod.config.BotConfig;
import io.github.icrazyblaze.twitchmod.integration.CarrierBeesIntegration;
import io.github.icrazyblaze.twitchmod.util.PlayerHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.Difficulty;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Supplier;


/**
 * This class is responsible for picking commands from chat and running them.
 * Command registering and blacklist operations are done in this class.
 */
public class ChatPicker {

    private static final Supplier<Path> blacklistPath = () -> FMLPaths.CONFIGDIR.get().resolve("twitch-blacklist.txt");
    private static final Map<String, Runnable> commandMap = new HashMap<>();
    public static List<String> blacklist;
    public static ArrayList<String> chatBuffer = new ArrayList<>();
    public static ArrayList<String> chatSenderBuffer = new ArrayList<>();
    public static boolean cooldownEnabled = false;
    public static boolean forceCommands = false;
    public static boolean instantCommands = false;
    public static boolean enabled = true;
    public static boolean logMessages = false;
    public static ArrayList<String> tempChatLog = new ArrayList<>();
    public static int chatLogLength = 10;
    private static File blacklistTextFile;
    private static boolean commandHasExecuted = false;
    private static String lastCommand = null;

    /**
     * @param toAdd The string to add to the blacklist file.
     */
    public static void addToBlacklist(String toAdd) {

        if (blacklist.contains(toAdd)) {
            return;
        }

        try {

            // Append to file
            FileWriter fr = new FileWriter(blacklistTextFile, true);

            // New line fix
            fr.write(System.lineSeparator() + toAdd);

            fr.close();

            // Update from file
            loadBlacklistFile();

        } catch (IOException e) {
            Main.logger.error(e);
        }

    }

    /**
     * Loads the blacklist file, or creates the file if it doesn't already exist.
     */
    public static void loadBlacklistFile() {

        blacklistTextFile = blacklistPath.get().toFile();
        try {

            blacklistTextFile.createNewFile(); // Create file if it doesn't already exist
            blacklist = Files.readAllLines(blacklistPath.get()); // Read into list

            // Remove all empty objects
            blacklist.removeAll(Arrays.asList("", null));

            // Remove prefixes from the start of commands in the blacklist
            for (int i = 0; i < blacklist.size(); i++) {
                if (blacklist.get(i).startsWith(BotConfig.prefix)) {

                    blacklist.set(i, blacklist.get(i).substring(BotConfig.prefix.length()));

                }
            }

        } catch (IOException e) {
            Main.logger.error(e);
        }

        // Fix for blacklist being null - set to empty instead
        if (blacklist == null) {
            blacklist = Collections.emptyList();
        }

    }

    /**
     * Clears the blacklist file.
     */
    public static void clearBlacklist() {

        try {

            // Clear text file using PrintWriter
            PrintWriter pr = new PrintWriter(blacklistTextFile);
            pr.close();

            // Update from file
            loadBlacklistFile();

        } catch (IOException e) {
            Main.logger.error(e);
        }

    }

    public static boolean isBlacklisted(String command) {

        if (!blacklist.isEmpty()) {
            return blacklist.contains(command);
        } else {
            return false;
        }

    }

    /**
     * Checks the command against the blacklist, unless force commands is enabled.
     *
     * @param message The chat message
     * @param sender  The sender's name
     */
    public static void checkChat(String message, String sender) {

        if (!enabled)
            return;

        // Remove the prefix
        if (message.startsWith(BotConfig.prefix)) {
            message = message.substring(BotConfig.prefix.length());

            if (!commandMap.containsKey(message))
                return;

        } else if (logMessages) {

            // If a message is not a command and temp logging is enabled, log the message
            String timeStamp = new SimpleDateFormat("[HH:mm:ss] ").format(new Date());
            tempChatLog.add(timeStamp + sender + ": " + message);

            // Add messages to book when there are enough
            if (tempChatLog.size() == chatLogLength) {

                // Add the chat messages to the book then stop recording chat
                CommandHandlers.createBook(tempChatLog);
                tempChatLog.clear();
                logMessages = false;

            }
            return;
        }

        // Skip checking if force commands is enabled
        if (forceCommands || instantCommands) {

            doCommand(message, sender);
            return;

        }


        // Only add the message if it is not blacklisted, and if the command isn't the same as the last
        if (isBlacklisted(message)) {
            Main.logger.info("Command not executed: command is blacklisted.");
            return;
        }
        if (lastCommand != null && cooldownEnabled) {

            if (!message.equalsIgnoreCase(lastCommand)) {

                chatBuffer.add(message);
                chatSenderBuffer.add(sender);

            } else {
                Main.logger.info(String.format("Command not executed: cooldown is active for this command (%s).", message));
            }

        } else {

            chatBuffer.add(message);
            chatSenderBuffer.add(sender);

        }

    }

    /**
     * Attempts to parse and then execute a command.
     *
     * @param message The chat command, e.g. "!creeper"
     * @param sender  The sender's name, which is used in some commands.
     * @return If the command doesn't run, then this method returns false.
     */
    public static boolean doCommand(String message, String sender) {

        if (!PlayerHelper.player().world.isRemote()) {

            // If the command contains a space, everything after the space is treated like an argument.
            // We chop of the arguments, and check the map for the command.

            // UPDATE: we now use the second part of this split to avoid cutting the commands off the beginning in actual functions.
            // e.g. before, showMessageBox() would be sent the whole message from twitch chat, and then substring the word "messagebox".

            String commandString;
            String argString;
            if (message.contains(" ")) {

                // Split at the space
                String[] split = message.split("\\s+");

                commandString = split[0]; // Before space (e.g. "messagebox")
                argString = message.substring(commandString.length());

            } else {
                commandString = message;
                argString = sender + " says hello!";
            }

            // Special commands below have extra arguments, so they are registered here.
            initDynamicCommands(argString, sender);

            try {
                // Invoke command from command map
                commandMap.get(commandString).run();

                if (BotConfig.showChatMessages && BotConfig.showCommandsInChat) {
                    CommandHandlers.broadcastMessage(new StringTextComponent(TextFormatting.AQUA + "Command Chosen: " + BotConfig.prefix + message));
                }

                // Below will not be executed if the command does not run
                lastCommand = message;
                return true;

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        return false;

    }

    /**
     * Adds a command to a list that ChatPicker checks.
     * The registerCommand method takes two arguments: a runnable, and any number of command aliases.
     * <pre>
     * {@code
     *     registerCommand(() -> CommandHandlers.myCommand(), "mycommand", "mycommandalias");
     * }
     * </pre>
     * If an entry with the same runnable or alias already exists, it will be replaced.
     * IDEA will swap the lambda for a method reference wherever possible.
     *
     * @param runnable The function linked to the command
     * @param keys     Aliases for the command
     */
    public static void registerCommand(Runnable runnable, String... keys) {

        /*
        This code is used to add multiple aliases for commands using hashmaps.
        Thank you gigaherz, very cool!
        */
        for (String key : keys) {

            // Don't register exactly the same command every time
            if (commandMap.containsKey(key) && commandMap.containsValue(runnable)) {
                commandMap.replace(key, runnable);
            } else {
                commandMap.put(key, runnable);
            }

        }

    }

    /**
     * Picks a random chat message, and checks if it is a command.
     * If the chat message is a command, it will be run. Otherwise, a new message is picked.
     */
    public static void pickRandomChat() {

        if (!chatBuffer.isEmpty()) {

            String message;
            String sender;
            Random rand = new Random();
            int listRandom = rand.nextInt(chatBuffer.size());

            message = chatBuffer.get(listRandom);
            sender = chatSenderBuffer.get(listRandom);

            commandHasExecuted = doCommand(message, sender);

            // If command is invalid
            if (!commandHasExecuted) {

                chatBuffer.remove(listRandom);
                commandFailed();

            }

            chatBuffer.clear();

        }

    }

    /**
     * Commands are registered here from doCommands.
     */
    public static void initCommands() {

        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.POISON, 400, 0)}), "poison");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.HUNGER, 800, 255)}), "hunger");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.SLOWNESS, 400, 5)}), "slowness");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.BLINDNESS, 400, 0)}), "blindness", "jinkies");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.SPEED, 400, 10)}), "speed", "gottagofast");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.NAUSEA, 400, 0)}), "nausea", "dontfeelsogood");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.MINING_FATIGUE, 400, 0)}), "fatigue");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.LEVITATION, 200, 1)}), "levitate", "fly");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.LEVITATION, 400, 255)}), "nofall", "float");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.LEVITATION, 200, 1)}), "levitate", "fly");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.HEALTH_BOOST, 400, 1), new EffectInstance(Effects.REGENERATION, 400, 1)}), "regen", "heal", "health");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.SATURATION, 200, 255)}), "saturation", "feed");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.JUMP_BOOST, 400, 2)}), "jumpboost", "yeet");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.HASTE, 400, 2)}), "haste", "diggydiggy");
        registerCommand(() -> CommandHandlers.addPotionEffects(new EffectInstance[]{new EffectInstance(Effects.BAD_OMEN, 400, 0)}), "badomen", "pillager", "raid");
        registerCommand(() -> PlayerHelper.player().clearActivePotions(), "cleareffects", "milk");
        registerCommand(CommandHandlers::setOnFire, "fire", "burn");
        registerCommand(CommandHandlers::floorIsLava, "lava", "floorislava");
        registerCommand(CommandHandlers::placeWater, "water", "watersbroke");
        registerCommand(CommandHandlers::placeSponge, "sponge");
        registerCommand(CommandHandlers::deathTimer, "timer", "deathtimer");
        registerCommand(CommandHandlers::graceTimer, "peacetimer", "timeout");
        registerCommand(CommandHandlers::drainHealth, "drain", "halfhealth");
        registerCommand(CommandHandlers::spawnAnvil, "anvil"); // Gaiet's favourite command <3
        registerCommand(() -> CommandHandlers.spawnMobBehind(EntityType.CREEPER.create(PlayerHelper.player().world)), "creeper", "awman");
        registerCommand(() -> CommandHandlers.spawnMobBehind(EntityType.ZOMBIE.create(PlayerHelper.player().world)), "zombie");
        registerCommand(() -> CommandHandlers.spawnMob(EntityType.ENDERMAN.create(PlayerHelper.player().world)), "enderman");
        registerCommand(() -> CommandHandlers.spawnMobBehind(EntityType.WITCH.create(PlayerHelper.player().world)), "witch");
        registerCommand(() -> CommandHandlers.spawnMobBehind(EntityType.SKELETON.create(PlayerHelper.player().world)), "skeleton");
        registerCommand(() -> CommandHandlers.spawnMobBehind(EntityType.SLIME.create(PlayerHelper.player().world)), "slime");
        registerCommand(CommandHandlers::spawnArmorStand, "armorstand", "armourstand", "boo");
        registerCommand(() -> CommandHandlers.playSound(SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.0F, 1.0F), "creeperscare", "behindyou");
        registerCommand(() -> CommandHandlers.playSound(SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundCategory.HOSTILE, 1.0F, 1.0F), "zombiescare", "bruh");
        registerCommand(() -> CommandHandlers.playSound(SoundEvents.ENTITY_SKELETON_AMBIENT, SoundCategory.HOSTILE, 1.0F, 1.0F), "skeletonscare", "spook");
        registerCommand(() -> CommandHandlers.playSound(SoundEvents.ENTITY_WITCH_AMBIENT, SoundCategory.HOSTILE, 1.0F, 1.0F), "witchscare", "hehe");
        registerCommand(() -> CommandHandlers.playSound(SoundEvents.ENTITY_GHAST_WARN, SoundCategory.HOSTILE, 10.0F, 1.0F), "ghastscare", "yikes");
        registerCommand(() -> CommandHandlers.playSound(SoundEvents.ENTITY_PHANTOM_AMBIENT, SoundCategory.HOSTILE, 10.0F, 1.0F), "phantomscare", "needsleep");
        registerCommand(CommandHandlers::pigmanScare, "pigmanscare", "aggro");
        registerCommand(() -> CommandHandlers.playSound(SoundEvents.BLOCK_ANVIL_FALL, SoundCategory.BLOCKS, 1.0F, 1.0F), "anvilscare");
        registerCommand(CommandHandlers::spawnLightning, "lightning");
        registerCommand(CommandHandlers::spawnFireball, "fireball");
        registerCommand(() -> CommandHandlers.oresExplode = true, "oresexplode");
        registerCommand(() -> CommandHandlers.placeBedrockOnBreak = true, "bedrock");
        registerCommand(() -> CommandHandlers.burnVillagersOnInteract = true, "villagersburn", "burnthemall");
        registerCommand(() -> CommandHandlers.destroyWorkbenchesOnInteract = true, "nocrafting", "breakworkbench");
        registerCommand(CommandHandlers::breakBlock, "break");
        registerCommand(CommandHandlers::dismount, "dismount", "getoff");
        registerCommand(CommandHandlers::dropItem, "drop", "throw");
        registerCommand(() -> PlayerHelper.player().inventory.dropAllItems(), "dropall");
        registerCommand(CommandHandlers::infestBlock, "silverfish");
        registerCommand(CommandHandlers::setRainAndThunder, "rain");
        registerCommand(() -> CommandHandlers.setDifficulty(Difficulty.HARD), "hardmode", "isthiseasymode");
        registerCommand(() -> CommandHandlers.setDifficulty(Difficulty.PEACEFUL), "peaceful", "peacefulmode");
        registerCommand(CommandHandlers::placeChest, "chest", "lootbox");
        registerCommand(() -> CommandHandlers.setTime(1000), "day", "setday");
        registerCommand(() -> CommandHandlers.setTime(13000), "night", "setnight");
        registerCommand(CommandHandlers::spawnCobweb, "cobweb", "stuck", "gbj");
        registerCommand(CommandHandlers::setSpawn, "spawnpoint", "setspawn");
        registerCommand(CommandHandlers::placeGlass, "glass");
        registerCommand(CommandHandlers::enchantItem, "enchant");
        registerCommand(CommandHandlers::curseArmour, "bind", "curse");
        registerCommand(CommandHandlers::startWritingBook, "book", "chatlog");
        registerCommand(CommandHandlers::toggleCrouch, "togglecrouch", "crouch");
        registerCommand(CommandHandlers::toggleSprint, "togglesprint", "sprint");
        registerCommand(CommandHandlers::pumpkin, "pumpkin");
        registerCommand(CommandHandlers::chorusTeleport, "chorusfruit", "chorus", "teleport");

    }

    /**
     * Commands that are registered here need to be re-added to the command registry every time they run because they have changing ("dynamic") elements.
     *
     * @param argString the argument for the command
     * @param sender    the name of the command sender
     */
    public static void initDynamicCommands(String argString, String sender) {

        registerCommand(() -> CommandHandlers.messWithInventory(sender), "itemroulette", "roulette");
        registerCommand(() -> CommandHandlers.shuffleInventory(sender), "shuffle");
        registerCommand(() -> CommandHandlers.showMessagebox(argString), "messagebox");
        registerCommand(() -> CommandHandlers.messagesList.add(argString), "addmessage");
        registerCommand(() -> CommandHandlers.placeSign(argString), "sign");
        registerCommand(() -> CommandHandlers.renameItem(argString), "rename");
        registerCommand(() -> CommandHandlers.rollTheDice(sender), "rtd", "roll", "dice");
        registerCommand(() -> FrenzyVote.vote(sender), "frenzy", "frenzymode", "suddendeath");

        // Carrier bees commands
        CarrierBeesIntegration.initDynamicCommands(sender);

    }

    public static List<String> getRegisteredCommands() {

        List<String> commandList = new ArrayList<>();

        for (String key : commandMap.keySet()) {

            if (!blacklist.contains(key)) {
                commandList.add(key);
            }

        }

        Collections.sort(commandList);
        return commandList;

    }

    public static void commandFailed() {

        if (!commandHasExecuted) {
            if (!chatBuffer.isEmpty()) {
                // Choose another if the list is big enough
                pickRandomChat();
            } else {
                Main.logger.error("Failed to execute a command.");
            }
        }

    }

}