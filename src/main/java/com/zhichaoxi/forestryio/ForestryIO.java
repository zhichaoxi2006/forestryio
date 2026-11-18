package com.zhichaoxi.forestryio;

import com.mojang.logging.LogUtils;
import com.zhichaoxi.forestryio.client.ForestryIOClient;
import com.zhichaoxi.forestryio.integration.ModCompat;
import com.zhichaoxi.forestryio.network.ForestryIONetwork;
import com.zhichaoxi.forestryio.registry.ForestryIODataComponents;
import com.zhichaoxi.forestryio.registry.ForestryIOItems;
import com.zhichaoxi.forestryio.registry.ForestryIOMenus;
import com.zhichaoxi.forestryio.registry.ForestryIOTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * Entry point of Forestry IO, an add-on that bridges Forestry: Community Edition and LaserIO.
 *
 * <p>Both mods are declared as required dependencies in {@code META-INF/neoforge.mods.toml}, so the
 * loader refuses to start without them. Integration code that touches their classes lives in
 * dedicated classes ({@link ModCompat}, {@code ForestryFilterItem}) so that a broken optional integration can
 * never take the mod's own entry point down.
 */
@Mod(ForestryIO.MOD_ID)
public final class ForestryIO {
    /** Must match {@code mod_id} in gradle.properties and in neoforge.mods.toml. */
    public static final String MOD_ID = "forestryio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ForestryIO(IEventBus modEventBus, ModContainer modContainer) {
        ForestryIOItems.register(modEventBus);
        ForestryIOTabs.register(modEventBus);
        ForestryIODataComponents.register(modEventBus);
        ForestryIOMenus.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> ForestryIONetwork.registerPayloads(event));
        if (FMLEnvironment.dist.isClient()) {
            // Client classes are only touched when this branch runs, so a dedicated server never loads them.
            ForestryIOClient.register(modEventBus);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // enqueueWork runs after all mods have been constructed and their registries are frozen.
        event.enqueueWork(() -> {
            LOGGER.info(
                    "Forestry IO loaded: LaserIO={}, Forestry={}, Patchouli={}",
                    ModCompat.isLoaded(ModCompat.LASERIO_MOD_ID),
                    ModCompat.isLoaded(ModCompat.FORESTRY_MOD_ID),
                    ModCompat.isLoaded(ModCompat.PATCHOULI_MOD_ID));

            // Force the mixin target to load now, so a broken hook is reported at startup instead of the first
            // time a player uses the filter. Mixin failures surface as an exception from class loading.
            try {
                Class.forName("com.direwolf20.laserio.util.BaseCardCache", false, ForestryIO.class.getClassLoader());
                LOGGER.info("Forestry Filter: LaserIO filter hook loaded.");
            } catch (Throwable throwable) {
                LOGGER.error("Forestry Filter: could not hook into LaserIO's filter logic; the filter will not match items.",
                        throwable);
            }
        });
    }
}
