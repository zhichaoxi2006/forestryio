package com.zhichaoxi.forestryio.mixin;

import com.direwolf20.laserio.common.network.handler.PacketOpenFilter;
import com.zhichaoxi.forestryio.item.ForestryFilterItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens the Forestry Filter screen when the filter is opened from inside a LaserIO card.
 *
 * <p>LaserIO's {@code PacketOpenFilter} maps each known filter class to its own container. Our filter extends
 * the data filter, which would otherwise open LaserIO's data filter screen instead.
 */
@Mixin(PacketOpenFilter.class)
public abstract class PacketOpenFilterMixin {
    @Inject(method = "doOpenFilter", at = @At("HEAD"), cancellable = true, remap = false)
    private static void forestryio$openForestryFilter(ItemStack filterItem, ItemStack cardItem, ServerPlayer sender,
                                                      BlockPos sourcePosition, CallbackInfo callback) {
        if (filterItem.getItem() instanceof ForestryFilterItem) {
            ForestryFilterItem.openMenu(sender, filterItem, cardItem, sourcePosition);
            callback.cancel();
        }
    }
}
