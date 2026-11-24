package io.github.itsgiantcookie.hstglabs.research;

import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.api.lab.IdleReasonType;
import io.github.itsgiantcookie.hstglabs.api.lab.RegisterIdleReasonsEvent;
import io.github.itsgiantcookie.hstglabs.core.IdleReason;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;

/**
 * Our own built-in extension entries, registered through the public events exactly as an addon would
 * (docs/design.md §9.6). WP-06 has the idle reasons; ingredient, reward, requirement and owner types
 * follow with WP-07 and WP-08.
 */
public final class BuiltInApi {

    private BuiltInApi() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(BuiltInApi::idleReasons);
    }

    private static void idleReasons(RegisterIdleReasonsEvent event) {
        for (String id : IdleReason.BUILT_IN) {
            ResourceLocation rl = ResourceLocation.parse(id);
            event.register(rl, new IdleReasonType("hstg_labs.idle." + rl.getPath(), HstgLabs.rl("textures/gui/idle/" + rl.getPath() + ".png")));
        }
    }
}
