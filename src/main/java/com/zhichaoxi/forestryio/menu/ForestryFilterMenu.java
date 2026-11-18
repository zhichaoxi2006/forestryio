package com.zhichaoxi.forestryio.menu;

import com.direwolf20.laserio.common.blockentities.LaserNodeBE;
import com.direwolf20.laserio.common.containers.customhandler.FilterBasicHandler;
import com.direwolf20.laserio.common.items.cards.BaseCard;
import com.direwolf20.laserio.common.items.filters.BaseFilter;
import com.zhichaoxi.forestryio.filter.ForestryFilterRule;
import com.zhichaoxi.forestryio.filter.ForestryGenetics;
import com.zhichaoxi.forestryio.item.ForestryFilterItem;
import com.zhichaoxi.forestryio.registry.ForestryIOMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Container of the Forestry Filter screen.
 *
 * <p>Layout matches LaserIO's data filter: the filter's single sample slot sits at the top right and the rest of
 * the screen is the chromosome list. The sample can be any Forestry genetic item (bee, sapling, pollen, ...) and
 * is a ghost slot (the item is copied, never consumed), so the screen does not need normal slot interaction.
 */
public class ForestryFilterMenu extends AbstractContainerMenu {
    /** Index of the sample slot; player inventory slots follow it. */
    public static final int SAMPLE_SLOT = 0;

    public final FilterBasicHandler handler;
    public final ItemStack filterItem;
    public final Player playerEntity;
    public ItemStack sourceCard = ItemStack.EMPTY;
    public BlockPos sourceContainer = BlockPos.ZERO;

    /** Client side constructor, reading the data written by {@link ForestryFilterItem#openMenu}. */
    public ForestryFilterMenu(int windowId, Inventory playerInventory, Player player, RegistryFriendlyByteBuf extraData) {
        this(windowId, playerInventory, player, ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
        this.sourceCard = ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData);
    }

    public ForestryFilterMenu(int windowId, Inventory playerInventory, Player player, ItemStack filterItem) {
        super(ForestryIOMenus.FORESTRY_FILTER.get(), windowId);
        this.playerEntity = player;
        this.filterItem = filterItem;
        this.handler = ForestryFilterItem.getSampleInventory(filterItem);
        this.addSlot(new ForestrySampleSlot(this.handler, SAMPLE_SLOT, 177, 6));
        this.layoutPlayerInventory(playerInventory);
    }

    /** Server side constructor used when the filter is opened from a LaserIO card. */
    public ForestryFilterMenu(int windowId, Inventory playerInventory, Player player, BlockPos sourcePosition,
                              ItemStack filterItem, ItemStack sourceCard) {
        this(windowId, playerInventory, player, filterItem);
        this.sourceContainer = sourcePosition;
        this.sourceCard = sourceCard;
    }

    private void layoutPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + column, 8 + column * 18, 172 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 230));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    @Override
    public void clicked(int slotId, int dragType, @NotNull ClickType clickType, @NotNull Player player) {
        // The sample slot is a ghost slot: it is filled by the screen and must not be picked up or swapped.
        if (slotId != SAMPLE_SLOT) {
            super.clicked(slotId, dragType, clickType, player);
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        if (index <= SAMPLE_SLOT) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem() || !this.handler.getStackInSlot(SAMPLE_SLOT).isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        if (!ForestryGenetics.isIndividual(stack)) {
            return ItemStack.EMPTY;
        }

        // Shift-click copies the sample into the ghost slot, like dragging it onto the sample slot.
        this.handler.setStackInSlot(SAMPLE_SLOT, stack.copyWithCount(1));
        return ItemStack.EMPTY;
    }

    /** Applies the configuration sent by the client screen. */
    public void applyClientRules(boolean allowList, List<String> encodedRules) {
        BaseFilter.setAllowList(this.filterItem, allowList);

        List<ForestryFilterRule> rules = new ArrayList<>();
        for (String encoded : encodedRules) {
            ForestryFilterRule rule = ForestryFilterRule.parse(encoded);
            if (rule != null && !rules.contains(rule)) {
                rules.add(rule);
            }
            if (rules.size() >= ForestryFilterRule.MAX_RULES) {
                break;
            }
        }

        ForestryFilterItem.setRules(this.filterItem, rules);
    }

    @Override
    public void removed(@NotNull Player player) {
        Level level = player.level();
        if (!level.isClientSide) {
            if (!this.sourceCard.isEmpty()) {
                BaseCard.getInventory(this.sourceCard).setStackInSlot(0, this.filterItem);
            }

            if (!this.sourceContainer.equals(BlockPos.ZERO)) {
                BlockEntity blockEntity = level.getBlockEntity(this.sourceContainer);
                if (blockEntity instanceof LaserNodeBE node) {
                    node.updateThisNode();
                }
            }
        }

        super.removed(player);
    }
}
