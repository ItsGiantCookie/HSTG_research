package de.germanrangers.hstglabs.client;

import de.germanrangers.hstglabs.HstgLabs;
import de.germanrangers.hstglabs.Registration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = HstgLabs.MODID, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Registration.RESEARCH_LAB_MENU.get(), ResearchLabScreen::new);
    }

    /** Client §9.2 registries: fired after parallel client setup has returned, then frozen. */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ClientApiRegistries::dispatch);
    }
}
