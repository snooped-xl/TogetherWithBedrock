package dev.snooped.bedrockmenu;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemPhysicDropCompatibilityTest {
    public static class ThrowSettings { public boolean enabled = true; }

    @Test void restoresConfiguredThrowingAfterVanillaDrop() throws Exception {
        var settings = new ThrowSettings();
        try (var scope = ItemPhysicDropCompatibility.Scope.disable(settings, ThrowSettings.class.getField("enabled"))) {
            assertFalse(settings.enabled);
        }
        assertTrue(settings.enabled);
    }

    @Test void restoresEvenWhenAnotherDropHookThrows() {
        var settings = new ThrowSettings();
        assertThrows(IllegalStateException.class, () -> {
            try (var scope = ItemPhysicDropCompatibility.Scope.disable(settings, ThrowSettings.class.getField("enabled"))) {
                throw new IllegalStateException("another mod's hook failed");
            }
        });
        assertTrue(settings.enabled);
    }

    @Test void respectsAlreadyDisabledAndNestedCalls() throws Exception {
        var settings = new ThrowSettings();
        var field = ThrowSettings.class.getField("enabled");
        try (var outer = ItemPhysicDropCompatibility.Scope.disable(settings, field)) {
            try (var inner = ItemPhysicDropCompatibility.Scope.disable(settings, field)) { assertFalse(settings.enabled); }
            assertFalse(settings.enabled);
        }
        settings.enabled = false;
        try (var scope = ItemPhysicDropCompatibility.Scope.disable(settings, field)) { assertFalse(settings.enabled); }
        assertFalse(settings.enabled);
    }
}
