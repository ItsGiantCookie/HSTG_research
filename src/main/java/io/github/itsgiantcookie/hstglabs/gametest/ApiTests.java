package io.github.itsgiantcookie.hstglabs.gametest;

import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.api.registry.RegisterIdEvent;
import io.github.itsgiantcookie.hstglabs.core.FreezableRegistry;
import io.github.itsgiantcookie.hstglabs.core.IdleReason;
import io.github.itsgiantcookie.hstglabs.demo.DemoAddon;
import io.github.itsgiantcookie.hstglabs.research.ApiRegistries;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * WP-06 acceptance (docs/design.md §13): the demo registers one of every extension point, a lab
 * requirement type included; every registry is frozen after its event, and registering afterwards
 * throws. Needs {@code -Dhstglabs.demo=true}, which the gameTestServer run sets.
 */
@GameTestHolder(HstgLabs.MODID)
@PrefixGameTestTemplate(false)
public final class ApiTests {

    private ApiTests() {
    }

    @GameTest(template = "empty")
    public static void demoRegistersOneOfEachExtensionPoint(GameTestHelper helper) {
        helper.assertTrue(DemoAddon.enabled(), "demo addon not enabled (-Dhstglabs.demo=true missing from the run)");
        Map<FreezableRegistry<ResourceLocation, ?>, ResourceLocation> expected = Map.of(
              ApiRegistries.INGREDIENT_TYPES, DemoAddon.INGREDIENT,
              ApiRegistries.REWARD_TYPES, DemoAddon.REWARD,
              ApiRegistries.OWNER_TYPES, DemoAddon.OWNER,
              ApiRegistries.TECH_SOURCES, DemoAddon.TECH_SOURCE,
              ApiRegistries.LAB_REQUIREMENT_TYPES, DemoAddon.REQUIREMENT,
              ApiRegistries.LAB_TYPE_SOURCES, DemoAddon.LAB_TYPE_SOURCE,
              ApiRegistries.LAB_STATS_PROVIDERS, DemoAddon.STATS_PROVIDER,
              ApiRegistries.MODIFIERS, DemoAddon.MODIFIER,
              ApiRegistries.IDLE_REASONS, DemoAddon.IDLE_REASON,
              ApiRegistries.TEMPLATE_KINDS, DemoAddon.TEMPLATE_KIND);
        List<ApiRegistries.Binding> bindings = ApiRegistries.bindings();
        helper.assertTrue(expected.size() == bindings.size(), "demo covers " + expected.size() + " of " + bindings.size() + " registries");
        for (ApiRegistries.Binding binding : bindings) {
            ResourceLocation id = expected.get(binding.registry());
            helper.assertTrue(id != null, "no demo entry expected for " + binding.registry().name());
            helper.assertTrue(binding.registry().contains(id), binding.registry().name() + " lacks demo entry " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registeringAfterDispatchThrows(GameTestHelper helper) {
        ResourceLocation late = HstgLabs.rl("too_late");
        for (ApiRegistries.Binding binding : ApiRegistries.bindings()) {
            helper.assertTrue(binding.registry().isFrozen(), binding.registry().name() + " is not frozen");
            RegisterIdEvent event = binding.event();
            try {
                // null passes the registrar's generic cast; the frozen check comes before the null check
                event.register(late, null);
                helper.fail(binding.registry().name() + " accepted a registration after dispatch");
            } catch (IllegalStateException expected) {
                helper.assertTrue(expected.getMessage().contains(binding.registry().name()), "unclear message: " + expected.getMessage());
            }
            helper.assertFalse(binding.registry().contains(late), binding.registry().name() + " kept the late entry");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void builtInIdleReasonsAreRegistered(GameTestHelper helper) {
        for (String id : IdleReason.BUILT_IN) {
            helper.assertTrue(ApiRegistries.IDLE_REASONS.contains(ResourceLocation.parse(id)), "idle reason not registered: " + id);
        }
        helper.succeed();
    }
}
