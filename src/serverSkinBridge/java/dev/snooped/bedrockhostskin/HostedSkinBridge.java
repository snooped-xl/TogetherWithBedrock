// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockhostskin;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional raw textures only for opted-in clients; Floodgate retains its signed vanilla skin path. */
public final class HostedSkinBridge implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("TogetherWithBedrock/HostedSkins");
    private static final int MAX_PLAYERS = 256, MAX_CACHE_BYTES = 32 * 1024 * 1024;
    private volatile Run current;

    @Override public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(SkinPayload.Capability.TYPE, SkinPayload.Capability.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SkinPayload.Capability.TYPE, SkinPayload.Capability.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SkinPayload.TYPE, SkinPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SkinPayload.Capability.TYPE, (payload, context) -> {
            Run run = current;
            if (payload.version() != 1 || run == null || run.server != context.server() || run.closed
                    || !ServerPlayNetworking.canSend(context.player(), SkinPayload.Capability.TYPE)
                    || !ServerPlayNetworking.canSend(context.player(), SkinPayload.TYPE)) return;
            UUID observer = context.player().getUUID();
            if (run.observers.size() >= MAX_PLAYERS || !run.observers.add(observer)) return;
            ServerPlayNetworking.send(context.player(), new SkinPayload.Capability(1));
            run.submit(() -> {
                List<SkinPayload> snapshot = run.cache.values().stream().map(Cached::payload).toList();
                run.server.execute(() -> snapshot.forEach(skin -> send(run, observer, skin)));
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Run run = current;
            if (run != null && run.server == server) run.observers.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> current = new Run(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            Run run = current;
            if (run != null && run.server == server) {
                current = null; run.closed = true; run.observers.clear(); run.worker.shutdownNow();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Run run = current;
            if (run == null || run.server != server || ++run.ticks % 20 != 0 || run.observers.isEmpty()
                    || !run.polling.compareAndSet(false, true)) return;
            Set<UUID> online = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) online.add(player.getUUID());
            run.submit(() -> { try { poll(run, online); } finally { run.polling.set(false); } });
        });
    }

    private void poll(Run run, Set<UUID> online) {
        try {
            Object geyser = run.api.callStatic("org.geysermc.geyser.GeyserImpl", "getInstance");
            if (geyser == null) return;
            Object floodgate = run.api.callStatic("org.geysermc.floodgate.api.FloodgateApi", "getInstance");
            Object manager = run.api.call(geyser, "getSessionManager");
            Object value = run.api.call(manager, "getSessions");
            if (!(value instanceof Map<?, ?> sessions)) return;
            Set<UUID> seen = new HashSet<>();
            for (Map.Entry<?, ?> entry : sessions.entrySet()) {
                if (seen.size() >= MAX_PLAYERS || run.closed) break;
                if (!(entry.getKey() instanceof UUID uuid) || !online.contains(uuid)) continue;
                Object session = entry.getValue();
                if (session == null || !Boolean.TRUE.equals(run.api.call(session, "isSpawned"))
                        || Boolean.TRUE.equals(run.api.call(session, "isClosed"))
                        || !Boolean.TRUE.equals(run.api.call(floodgate, "isFloodgatePlayer", UUID.class, uuid))
                        || !uuid.equals(run.api.call(session, "javaUuid"))) continue;
                seen.add(uuid);
                RawSkin raw = raw(run.api, run.api.call(session, "getClientData"));
                if (raw == null) { remove(run, uuid); continue; }
                byte[] fingerprint = raw.fingerprint();
                Cached old = run.cache.get(uuid);
                if (old != null && Arrays.equals(old.fingerprint(), fingerprint)) continue;
                SkinPayload skin = new SkinPayload(uuid, raw.flags(), png(raw.skin(), raw.width(), raw.height()),
                        raw.cape().length == 0 ? new byte[0] : png(raw.cape(), raw.capeWidth(), raw.capeHeight()));
                int replacingBytes = old == null ? 0 : old.payload().bytes();
                if (run.bytes - replacingBytes + skin.bytes() > MAX_CACHE_BYTES) { remove(run, uuid); continue; }
                run.cache.put(uuid, new Cached(fingerprint, skin));
                run.bytes += skin.bytes() - replacingBytes;
                broadcast(run, skin);
            }
            for (UUID uuid : List.copyOf(run.cache.keySet())) if (!seen.contains(uuid)) remove(run, uuid);
            run.warned = false;
        } catch (ReflectiveOperationException | RuntimeException | java.io.IOException | java.security.NoSuchAlgorithmException e) {
            if (!run.warned && !run.closed) {
                LOG.warn("Hosted skin bridge could not read authenticated Geyser skins; native Floodgate skins remain enabled ({})", e.getClass().getSimpleName());
                run.warned = true;
            }
        }
    }

    private static RawSkin raw(Api api, Object data) throws ReflectiveOperationException {
        if (data == null) return null;
        int width = ((Number) api.call(data, "getSkinImageWidth")).intValue();
        int height = ((Number) api.call(data, "getSkinImageHeight")).intValue();
        byte[] skin = (byte[]) api.call(data, "getSkinData");
        if (!validRgba(skin, width, height) || !(width == 64 || width == 128 || width == 256)
                || !(height == width || height * 2 == width)) return null;
        boolean persona = Boolean.TRUE.equals(api.call(data, "isPersonaSkin"));
        String arm = Objects.toString(api.call(data, "getArmSize"), "");
        byte[] geometry = (byte[]) api.call(data, "getGeometryName");
        boolean slim = "slim".equalsIgnoreCase(arm) || geometry != null && geometry.length <= 8192
                && new String(geometry, StandardCharsets.UTF_8).contains("geometry.humanoid.customSlim");
        byte[] cape = (byte[]) api.call(data, "getCapeData");
        int capeWidth = ((Number) api.call(data, "getCapeImageWidth")).intValue();
        int capeHeight = ((Number) api.call(data, "getCapeImageHeight")).intValue();
        if (!validRgba(cape, capeWidth, capeHeight)) { cape = new byte[0]; capeWidth = capeHeight = 0; }
        return new RawSkin(width, height, skin, capeWidth, capeHeight, cape, (slim ? 1 : 0) | (persona ? 2 : 0));
    }

    static boolean validRgba(byte[] bytes, int width, int height) {
        return bytes != null && width > 0 && width <= 256 && height > 0 && height <= 256 && bytes.length == width * height * 4;
    }
    static byte[] png(byte[] rgba, int width, int height) throws java.io.IOException {
        if (!validRgba(rgba, width, height)) throw new java.io.IOException("Invalid hosted skin dimensions");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0, offset = 0; y < height; y++) for (int x = 0; x < width; x++, offset += 4) {
            int color = (rgba[offset + 3] & 255) << 24 | (rgba[offset] & 255) << 16 | (rgba[offset + 1] & 255) << 8 | rgba[offset + 2] & 255;
            image.setRGB(x, y, color);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", output)) throw new java.io.IOException("PNG encoding is unavailable");
        return output.toByteArray();
    }

    private void remove(Run run, UUID uuid) {
        Cached old = run.cache.remove(uuid);
        if (old != null) { run.bytes -= old.payload().bytes(); broadcast(run, SkinPayload.remove(uuid)); }
    }
    private void broadcast(Run run, SkinPayload skin) {
        run.server.execute(() -> { for (UUID observer : run.observers) send(run, observer, skin); });
    }
    private void send(Run run, UUID observer, SkinPayload skin) {
        if (run.closed || run != current || !run.observers.contains(observer) || observer.equals(skin.uuid())) return;
        ServerPlayer player = run.server.getPlayerList().getPlayer(observer);
        if (player != null && ServerPlayNetworking.canSend(player, SkinPayload.TYPE)) ServerPlayNetworking.send(player, skin);
    }

    private record Cached(byte[] fingerprint, SkinPayload payload) {}
    private record RawSkin(int width, int height, byte[] skin, int capeWidth, int capeHeight, byte[] cape, int flags) {
        byte[] fingerprint() throws java.security.NoSuchAlgorithmException {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(20).putInt(width).putInt(height).putInt(capeWidth).putInt(capeHeight).putInt(flags).array());
            digest.update(skin); digest.update(cape); return digest.digest();
        }
    }
    private static final class Run {
        final MinecraftServer server;
        final Set<UUID> observers = ConcurrentHashMap.newKeySet();
        final Map<UUID, Cached> cache = new LinkedHashMap<>(); // Worker-owned; no rendering or PNG work on the tick thread.
        final Api api = new Api();
        final AtomicBoolean polling = new AtomicBoolean();
        final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "TogetherWithBedrock hosted skins"); thread.setDaemon(true); return thread;
        });
        volatile boolean closed;
        boolean warned;
        int ticks, bytes;
        Run(MinecraftServer server) { this.server = server; }
        void submit(Runnable task) { if (!closed) try { worker.execute(task); } catch (RejectedExecutionException ignored) {} }
    }
    private static final class Api {
        private final Map<String, Method> methods = new HashMap<>();
        Object callStatic(String owner, String name) throws ReflectiveOperationException { return method(Class.forName(owner), name).invoke(null); }
        Object call(Object owner, String name) throws ReflectiveOperationException { return method(owner.getClass(), name).invoke(owner); }
        Object call(Object owner, String name, Class<?> type, Object value) throws ReflectiveOperationException { return method(owner.getClass(), name, type).invoke(owner, value); }
        private Method method(Class<?> owner, String name, Class<?>... arguments) throws NoSuchMethodException {
            String key = owner.getName() + '#' + name + Arrays.toString(arguments);
            Method result = methods.get(key);
            if (result == null) { result = owner.getMethod(name, arguments); methods.put(key, result); }
            return result;
        }
    }
}
