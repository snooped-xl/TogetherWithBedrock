// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Reports Java's otherwise invisible creative cursor gestures to our negotiated local proxy. */
public final class CreativeBridge {
    private static Connection connection;
    private static boolean enabled;
    private static ServerboundContainerClosePacket pendingClose;
    private static boolean closing;
    private static int lastAcknowledged, closeSequence;
    private static int sequence;
    private static final CreativeRequestQueue<Gesture> queue = new CreativeRequestQueue<>();
    private static ItemStack[] before;
    private static ItemStack carriedBefore;
    private static final List<ServerboundSetCreativeModeSlotPacket> vanillaPackets = new ArrayList<>();
    private static final List<ItemStack> drops = new ArrayList<>();

    private CreativeBridge() { }

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Capability.TYPE, Capability.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Capability.TYPE, Capability.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Gesture.TYPE, Gesture.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Capability.TYPE, (payload, context) -> {
            if (payload.version() == 1 && context.client().getConnection() != null
                    && context.client().getConnection().getConnection() == connection && active()) enabled = true;
        });
        ClientPlayNetworking.registerGlobalReceiver(State.TYPE, (payload, context) -> {
            var client = context.client();
            if (!enabled || !active() || client.player == null) return;
            boolean closeReply = closing && payload.sequence() == closeSequence;
            var completion = closeReply ? new CreativeRequestQueue.Completion<Gesture>(true, null)
                    : queue.complete(payload.sequence(), payload.accepted());
            if (!completion.matched()) return;
            lastAcknowledged = payload.sequence();
            if (closeReply) closing = false;
            if (completion.next() != null) {
                send(completion.next().value());
                return;
            }
            // A rejected gesture discards its dependent optimistic clicks. A final
            // successful ACK also repairs any inventory updates deferred while busy.
            for (SlotValue slot : payload.slots()) client.player.inventoryMenu.getSlot(slot.slot()).set(slot.item().copy());
            client.player.inventoryMenu.setCarried(payload.carried().copy());
            if (pendingClose != null) {
                var close = pendingClose; pendingClose = null;
                client.getConnection().send(close);
            }
        });
    }

    static void join(Connection joined) {
        reset();
        connection = joined;
        if (active()) Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new Capability(1)));
    }

    static void reset() {
        connection = null; enabled = false; sequence = 0; lastAcknowledged = 0; closeSequence = 0; closing = false; queue.clear(); pendingClose = null; before = null; carriedBefore = null; drops.clear(); vanillaPackets.clear();
    }

    private static boolean active() {
        return BedrockMenu.INSTANCE != null && BedrockMenu.INSTANCE.isActiveProxyConnection(connection);
    }

    public static boolean begin() {
        var client = Minecraft.getInstance();
        if (!enabled || !active() || client.player == null || !client.player.isCreative() || before != null) return false;
        before = new ItemStack[46];
        for (int i = 0; i < before.length; i++) before[i] = client.player.inventoryMenu.getSlot(i).getItem().copy();
        carriedBefore = client.player.inventoryMenu.getCarried().copy();
        drops.clear(); vanillaPackets.clear();
        return true;
    }

    public static boolean deferInventoryUpdate() { return enabled && (queue.pending() || closing) && active(); }
    public static boolean closingInventory() { return enabled && (closing || pendingClose != null) && active(); }

    private static void send(Gesture gesture) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(gesture));
    }

    public static boolean captureSlot() { return before != null && enabled && active(); }

    public static boolean capturePacket(Packet<?> packet) {
        if (packet instanceof ServerboundContainerClosePacket close && close.getContainerId() == 0 && enabled && active()) {
            if (closing) return true;
            if (queue.pending()) { pendingClose = close; return true; }
            closing = true; closeSequence = lastAcknowledged;
            return false;
        }
        if (!captureSlot() || !(packet instanceof ServerboundSetCreativeModeSlotPacket creative)) return false;
        ItemStack item = creative.itemStack().copy();
        vanillaPackets.add(new ServerboundSetCreativeModeSlotPacket(creative.slotNum(), item));
        if (creative.slotNum() == -1 && !item.isEmpty()) drops.add(item);
        return true;
    }

    /** Always clear scope on exceptional UI exit; ordinary Java packet sending remains usable. */
    public static void abort() {
        if (before == null) return;
        var client = Minecraft.getInstance();
        if (active() && client.player != null) {
            for (int i = 0; i < before.length; i++) client.player.inventoryMenu.getSlot(i).set(before[i].copy());
            client.player.inventoryMenu.setCarried(carriedBefore.copy());
        }
        before = null; carriedBefore = null; drops.clear(); vanillaPackets.clear();
    }

    public static void end() {
        if (before == null) return;
        var client = Minecraft.getInstance();
        try {
            if (!enabled || !active() || client.player == null) return;
            List<SlotValue> changed = new ArrayList<>();
            for (int i = 0; i < before.length; i++) {
                ItemStack after = client.player.inventoryMenu.getSlot(i).getItem();
                if (!ItemStack.matches(before[i], after)) changed.add(new SlotValue(i, after.copy()));
            }
            ItemStack carried = client.player.inventoryMenu.getCarried().copy();
            if (changed.isEmpty() && drops.isEmpty() && ItemStack.matches(carriedBefore, carried)) return;
            // A creative click cannot legitimately emit more than eight drop stacks.
            if (drops.size() > 8) throw new IllegalStateException("Too many creative drop operations");
            var gesture = new Gesture(++sequence, List.copyOf(changed), carried, List.copyOf(drops));
            // Reject oversized custom metadata locally before Netty's payload limit
            // could close the connection. No vanilla slot edits have left this scope.
            var encoded = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), client.player.registryAccess());
            try {
                Gesture.CODEC.encode(encoded, gesture);
                if (encoded.readableBytes() > 32767) throw new IllegalArgumentException("Creative item data exceeds the proxy message limit");
            } finally { encoded.release(); }
            var next = queue.offer(gesture.sequence(), gesture);
            if (next != null) send(next.value());
        } catch (RuntimeException error) {
            abort();
            if (client.player != null) client.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.literal("That creative edit could not be sent; the items were restored."), true);
            com.mojang.logging.LogUtils.getLogger().debug("Creative gesture restored before sending", error);
        } finally {
            before = null; carriedBefore = null; drops.clear(); vanillaPackets.clear();
        }
    }

    record SlotValue(int slot, ItemStack item) { }
    record Capability(int version) implements CustomPacketPayload {
        static final Type<Capability> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bedrockmenu", "creative_bridge"));
        static final StreamCodec<RegistryFriendlyByteBuf, Capability> CODEC = CustomPacketPayload.codec(
                (value, buffer) -> buffer.writeVarInt(value.version), buffer -> new Capability(buffer.readVarInt()));
        public Type<Capability> type() { return TYPE; }
    }
    record Gesture(int sequence, List<SlotValue> slots, ItemStack carried, List<ItemStack> drops) implements CustomPacketPayload {
        static final Type<Gesture> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bedrockmenu", "creative_gesture"));
        static final StreamCodec<RegistryFriendlyByteBuf, Gesture> CODEC = CustomPacketPayload.codec(Gesture::write, Gesture::read);
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(1); buffer.writeVarInt(sequence); writeSlots(buffer, slots);
            ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(buffer, carried);
            buffer.writeVarInt(drops.size());
            for (ItemStack drop : drops) ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(buffer, drop);
        }
        private static Gesture read(RegistryFriendlyByteBuf buffer) {
            version(buffer); int sequence = buffer.readVarInt(); List<SlotValue> slots = readSlots(buffer);
            ItemStack carried = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(buffer);
            int size = bounded(buffer.readVarInt(), 8); List<ItemStack> drops = new ArrayList<>(size);
            for (int i = 0; i < size; i++) drops.add(ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(buffer));
            return new Gesture(sequence, slots, carried, drops);
        }
        public Type<Gesture> type() { return TYPE; }
    }
    record State(int sequence, boolean accepted, List<SlotValue> slots, ItemStack carried) implements CustomPacketPayload {
        static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bedrockmenu", "creative_state"));
        static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = CustomPacketPayload.codec(State::write, State::read);
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(1); buffer.writeVarInt(sequence); buffer.writeBoolean(accepted); writeSlots(buffer, slots);
            ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(buffer, carried);
        }
        private static State read(RegistryFriendlyByteBuf buffer) {
            version(buffer); int sequence = buffer.readVarInt(); boolean accepted = buffer.readBoolean();
            return new State(sequence, accepted, readSlots(buffer), ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(buffer));
        }
        public Type<State> type() { return TYPE; }
    }
    private static void version(RegistryFriendlyByteBuf buffer) {
        if (buffer.readVarInt() != 1) throw new IllegalArgumentException("Unsupported creative bridge version");
    }
    private static int bounded(int value, int maximum) {
        if (value < 0 || value > maximum) throw new IllegalArgumentException("Invalid creative bridge count");
        return value;
    }
    private static void writeSlots(RegistryFriendlyByteBuf buffer, List<SlotValue> slots) {
        buffer.writeVarInt(slots.size());
        for (SlotValue slot : slots) { buffer.writeShort(slot.slot()); ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(buffer, slot.item()); }
    }
    private static List<SlotValue> readSlots(RegistryFriendlyByteBuf buffer) {
        int size = bounded(buffer.readVarInt(), 46); List<SlotValue> slots = new ArrayList<>(size); boolean[] seen = new boolean[46];
        for (int i = 0; i < size; i++) {
            int slot = bounded(buffer.readShort(), 45);
            if (seen[slot]) throw new IllegalArgumentException("Duplicate creative bridge slot");
            seen[slot] = true;
            slots.add(new SlotValue(slot, ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(buffer)));
        }
        return slots;
    }
}
