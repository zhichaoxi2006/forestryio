package com.zhichaoxi.forestryio.registry;

import com.zhichaoxi.forestryio.ForestryIO;
import com.zhichaoxi.forestryio.menu.ForestryFilterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menu (container) registry of Forestry IO. */
public final class ForestryIOMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ForestryIO.MOD_ID);

    /** The Forestry Filter configuration screen; the entity data carries the filter stack and its source card. */
    public static final DeferredHolder<MenuType<?>, MenuType<ForestryFilterMenu>> FORESTRY_FILTER =
            MENUS.register("forestry_filter", () -> IMenuTypeExtension.create(
                    (windowId, inventory, data) -> new ForestryFilterMenu(windowId, inventory, inventory.player, data)));

    private ForestryIOMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
