package com.zhichaoxi.forestryio.registry;

import com.zhichaoxi.forestryio.ForestryIO;
import com.zhichaoxi.forestryio.item.ForestryFilterItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registry. Add new items here and expose them through {@link ForestryIOTabs}. */
public final class ForestryIOItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ForestryIO.MOD_ID);

    /** LaserIO filter that keeps or rejects Forestry's genetic items by their genome. */
    public static final DeferredItem<ForestryFilterItem> FORESTRY_FILTER =
            ITEMS.register("forestry_filter", ForestryFilterItem::new);

    private ForestryIOItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
