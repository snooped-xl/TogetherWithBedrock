package dev.snooped.bedrockmenu;

import net.minecraft.client.player.LocalPlayer;

/** Bedrock's physics transition is independent of Java's visual swimming/crawling blend. */
public final class BedrockSwimState {
    private static LocalPlayer player;
    private static Transition transition = new Transition();

    private BedrockSwimState() {}

    /** Called before liquid jumping, after this tick's actual swimming state has been updated. */
    public static void beginTick(LocalPlayer current) {
        if (player != current) {
            reset();
            player = current;
        }
        transition.advance(current.tickCount, current.isSwimming());
    }

    public static float amount(LocalPlayer current) {
        return player == current && transition.isCurrent(current.tickCount) ? transition.amount() : 0F;
    }

    public static boolean stoppedThisTick(LocalPlayer current) {
        return player == current && transition.isCurrent(current.tickCount) && transition.stopped();
    }

    public static void reset() {
        player = null;
        transition = new Transition();
    }

    static final class Transition {
        private boolean initialized;
        private int tick;
        private boolean previousSwimming;
        private boolean stopped;
        private float amount;

        void advance(int currentTick, boolean swimming) {
            if (initialized && currentTick == tick) return;
            if (!initialized || currentTick != tick + 1) {
                // Respawning, changing physics mode, or skipping simulation must not retain a stale edge.
                previousSwimming = false;
                amount = 0F;
            }
            // Bedrock advances from the state BEFORE this tick's start/stop swimming input.
            amount = Math.clamp(amount + (previousSwimming ? 0.1F : -0.1F), 0F, 1F);
            if (amount < 0.000001F) amount = 0F;
            else if (amount > 0.999999F) amount = 1F;
            stopped = previousSwimming && !swimming;
            previousSwimming = swimming;
            tick = currentTick;
            initialized = true;
        }

        boolean isCurrent(int currentTick) { return initialized && tick == currentTick; }
        float amount() { return amount; }
        boolean stopped() { return stopped; }
    }
}
