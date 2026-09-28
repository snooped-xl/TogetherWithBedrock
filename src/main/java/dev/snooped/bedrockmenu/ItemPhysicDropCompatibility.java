package dev.snooped.bedrockmenu;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;

/** ItemPhysic Full otherwise cancels vanilla Q and sends an unsupported custom packet. */
public final class ItemPhysicDropCompatibility {
    private static boolean resolved;
    private static Field config, throwConfig, enabled, charge;
    private static Identifier dropChannel;
    private static Object loggedConnection;

    private ItemPhysicDropCompatibility() { }

    /** Only changes the switch for the duration of the affected client call, never saved configuration. */
    public static Scope vanillaDropScope() {
        try {
            if (!resolve()) return Scope.NONE;
            Minecraft client = Minecraft.getInstance();
            var listener = client.getConnection();
            if (listener == null || client.hasSingleplayerServer() || ClientPlayNetworking.canSend(dropChannel)) return Scope.NONE;
            Object settings = throwConfig.get(config.get(null));
            // A held throw must not carry over to a different connection later.
            charge.setInt(null, 0);
            Scope scope = Scope.disable(settings, enabled);
            if (scope != Scope.NONE && loggedConnection != listener.getConnection()) {
                loggedConnection = listener.getConnection();
                LogUtils.getLogger().info("[TogetherWithBedrock] Using vanilla Q drops: this server does not support ItemPhysic charged throws.");
            }
            return scope;
        } catch (ReflectiveOperationException | LinkageError error) {
            dropChannel = null;
            LogUtils.getLogger().warn("[TogetherWithBedrock] ItemPhysic drop compatibility unavailable", error);
            return Scope.NONE;
        }
    }

    private static boolean resolve() throws ReflectiveOperationException {
        if (resolved) return dropChannel != null;
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded("itemphysic")) return false;
        Class<?> mod = Class.forName("team.creative.itemphysic.ItemPhysic");
        config = mod.getField("CONFIG");
        throwConfig = config.getType().getField("throwConfig");
        enabled = throwConfig.getType().getField("enabled");
        charge = Class.forName("team.creative.itemphysic.client.ItemPhysicClient").getField("throwCharge");
        Object network = mod.getField("NETWORK").get(null);
        Class<?> drop = Class.forName("team.creative.itemphysic.common.packet.DropPacket");
        Object type = network.getClass().getMethod("getPacketType", Class.class).invoke(network, drop);
        if (type == null) throw new ReflectiveOperationException("ItemPhysic DropPacket is not registered");
        // Resolve the registered packet rather than guessing a channel or accepting any CreativeCore mod.
        dropChannel = ((CustomPacketPayload.Type<?>) type.getClass().getField("sid").get(type)).id();
        return true;
    }

    public static final class Scope implements AutoCloseable {
        static final Scope NONE = new Scope(null, null);
        private final Object settings;
        private final Field enabled;

        private Scope(Object settings, Field enabled) { this.settings = settings; this.enabled = enabled; }

        static Scope disable(Object settings, Field enabled) throws IllegalAccessException {
            if (!enabled.getBoolean(settings)) return NONE;
            enabled.setBoolean(settings, false);
            return new Scope(settings, enabled);
        }

        @Override public void close() {
            if (enabled != null) {
                try { enabled.setBoolean(settings, true); }
                catch (IllegalAccessException error) { LogUtils.getLogger().warn("Unable to restore ItemPhysic throw setting", error); }
            }
        }
    }
}
