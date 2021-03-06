package io.github.icrazyblaze.twitchmod.network;

import io.github.icrazyblaze.twitchmod.Main;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class PacketHandler {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Main.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static int ID = 0;

    public static void registerMessages() {

        INSTANCE.registerMessage(nextID(),
                MessageboxPacket.class,
                MessageboxPacket::toBytes,
                MessageboxPacket::new,
                MessageboxPacket::handle);

    }

    public static int nextID() {
        return ID++;
    }

}