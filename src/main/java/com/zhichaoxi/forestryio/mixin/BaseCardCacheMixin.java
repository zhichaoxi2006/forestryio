package com.zhichaoxi.forestryio.mixin;

import com.direwolf20.laserio.util.BaseCardCache;
import com.zhichaoxi.forestryio.item.ForestryFilterItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Teaches LaserIO's card cache about the Forestry Filter.
 *
 * <p>LaserIO decides whether an item may pass a card with {@code BaseCardCache#isStackValidForCard}, and that
 * method only knows its own filter classes ({@code FilterBasic}, {@code FilterCount}, {@code FilterTag},
 * {@code FilterNBT}, {@code FilterMod}). A new filter item therefore has to intercept it before LaserIO falls
 * through to the "match the filter's item list" branch, which would reject every genetic item.
 */
@Mixin(BaseCardCache.class)
public abstract class BaseCardCacheMixin {
    @Inject(
            method = "isStackValidForCard(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void forestryio$matchForestryFilter(ItemStack testStack, CallbackInfoReturnable<Boolean> callback) {
        BaseCardCache cache = (BaseCardCache) (Object) this;
        if (cache.filterCard.getItem() instanceof ForestryFilterItem) {
            callback.setReturnValue(ForestryFilterItem.matches(cache.filterCard, testStack));
        }
    }
}
