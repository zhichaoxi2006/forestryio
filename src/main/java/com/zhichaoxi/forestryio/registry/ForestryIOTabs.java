package com.zhichaoxi.forestryio.registry;

import com.zhichaoxi.forestryio.ForestryIO;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Creative tab shown in the Forestry IO item group. */
public final class ForestryIOTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ForestryIO.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.forestryio"))
            .icon(() -> new ItemStack(ForestryIOItems.FORESTRY_FILTER.get()))
            .displayItems((parameters, output) -> output.accept(ForestryIOItems.FORESTRY_FILTER.get()))
            .build());

    private ForestryIOTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
