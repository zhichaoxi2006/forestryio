package com.zhichaoxi.forestryio.menu;

import com.direwolf20.laserio.common.containers.customhandler.FilterBasicHandler;
import com.direwolf20.laserio.common.containers.customslot.FilterBasicSlot;
import com.zhichaoxi.forestryio.filter.ForestryGenetics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The ghost slot at the top right of the Forestry Filter screen. It accepts any Forestry genetic individual
 * (bee, sapling, pollen, butterfly, ...), because the sample is only used to list the chromosomes the filter can
 * be configured with.
 */
public class ForestrySampleSlot extends FilterBasicSlot {
    public ForestrySampleSlot(FilterBasicHandler handler, int index, int x, int y) {
        super(handler, index, x, y, false);
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return stack.isEmpty() || ForestryGenetics.isIndividual(stack);
    }
}
