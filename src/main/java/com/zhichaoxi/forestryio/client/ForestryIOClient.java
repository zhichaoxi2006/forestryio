package com.zhichaoxi.forestryio.client;

import com.zhichaoxi.forestryio.client.screen.ForestryFilterScreen;
import com.zhichaoxi.forestryio.registry.ForestryIOMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only setup.
 *
 * <p>Registered explicitly from {@code ForestryIO} behind a {@code Dist.CLIENT} check (the same pattern LaserIO
 * uses for its own client setup) so no client class is ever loaded on a dedicated server.
 */
public final class ForestryIOClient {
    private ForestryIOClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterMenuScreensEvent event) -> registerScreens(event));
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ForestryIOMenus.FORESTRY_FILTER.get(), ForestryFilterScreen::new);
    }
}
