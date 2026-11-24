package de.germanrangers.hstglabs.client;

import de.germanrangers.hstglabs.HstgLabs;
import de.germanrangers.hstglabs.Registration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import de.germanrangers.hstglabs.tech.PackLookup;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = HstgLabs.MODID, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Registration.RESEARCH_LAB_MENU.get(), ResearchLabScreen::new);
    }

    /** Science packs: layer 1 (the liquid) takes the pack's colour from the synced pack definitions. */
    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) {
                return -1;
            }
            ResourceLocation pack = stack.get(Registration.PACK_COMPONENT.get());
            return pack == null ? -1 : PackLookup.get(pack).map(p -> 0xFF000000 | p.color()).orElse(-1);
        }, Registration.SCIENCE_PACK.get());
    }

    /** Client §9.2 registries: fired after parallel client setup has returned, then frozen. */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ClientApiRegistries::dispatch);
    }
}
