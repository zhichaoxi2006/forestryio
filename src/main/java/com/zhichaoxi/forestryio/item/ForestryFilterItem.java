package com.zhichaoxi.forestryio.item;

import com.direwolf20.laserio.common.containers.customhandler.FilterBasicHandler;
import com.direwolf20.laserio.common.items.filters.BaseFilter;
import com.direwolf20.laserio.common.items.filters.FilterNBT;
import com.zhichaoxi.forestryio.filter.ForestryFilterRule;
import com.zhichaoxi.forestryio.filter.ForestryGenetics;
import com.zhichaoxi.forestryio.menu.ForestryFilterMenu;
import com.zhichaoxi.forestryio.registry.ForestryIODataComponents;
import forestry.api.core.genetics.IGenome;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Forestry Filter: a LaserIO filter that keeps or rejects Forestry's genetic items based on their genome.
 *
 * <p>It extends LaserIO's data filter so that LaserIO itself treats it as a "one sample, no ghost slot grid"
 * filter inside cards; the actual matching is performed by the {@code BaseCardCacheMixin}. Its screen reuses the
 * data filter layout, but the list shows the sample's chromosomes instead of data component names.
 *
 * <p>The sample can be any Forestry individual - a bee, a sapling, pollen or a butterfly - and the list is built
 * from that item's species type. Every rule is "species type + chromosome + side (active/inactive) + allele".
 * Rules are combined with AND per species type: an item only has to satisfy the rules that belong to its own
 * species type, and an item whose type has no rules never matches. The whole set can be inverted with LaserIO's
 * shared allow/deny flag.
 */
public class ForestryFilterItem extends FilterNBT {
    /** Number of rules shown in the item tooltip before the list is abbreviated. */
    private static final int TOOLTIP_RULE_LIMIT = 6;

    public ForestryFilterItem() {
        super();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return new InteractionResultHolder<>(InteractionResult.PASS, stack);
        }

        openMenu(serverPlayer, stack, ItemStack.EMPTY, BlockPos.ZERO);
        return new InteractionResultHolder<>(InteractionResult.PASS, stack);
    }

    /**
     * Opens the configuration screen. {@code sourceCard} and {@code sourcePosition} are only used when the filter
     * is opened from inside a LaserIO card, so that the edited copy is written back on close (same flow as
     * LaserIO's own filters).
     */
    public static void openMenu(ServerPlayer player, ItemStack filter, ItemStack sourceCard, BlockPos sourcePosition) {
        player.openMenu(
                new SimpleMenuProvider(
                        (windowId, inventory, playerEntity) ->
                                new ForestryFilterMenu(windowId, inventory, playerEntity, sourcePosition, filter, sourceCard),
                        Component.translatable("container.forestryio.forestry_filter")),
                buffer -> {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, filter);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, sourceCard);
                });
    }

    /** The single sample slot handler of the configuration screen. */
    public static FilterBasicHandler getSampleInventory(ItemStack filter) {
        return new FilterBasicHandler(1, filter);
    }

    /** @return the serialized rules stored on the filter, never {@code null}. */
    public static List<String> getRuleStrings(ItemStack filter) {
        return filter.getOrDefault(ForestryIODataComponents.FILTER_RULES.get(), List.of());
    }

    public static void setRuleStrings(ItemStack filter, List<String> encoded) {
        if (encoded.isEmpty()) {
            filter.remove(ForestryIODataComponents.FILTER_RULES.get());
        } else {
            filter.set(ForestryIODataComponents.FILTER_RULES.get(), List.copyOf(encoded));
        }
    }

    /** @return the parsed rules of the filter, skipping entries that can no longer be resolved. */
    public static List<ForestryFilterRule> getRules(ItemStack filter) {
        return ForestryFilterRule.parseAll(getRuleStrings(filter));
    }

    /** Replaces the rules of the filter with the given ones (duplicates and unresolvable rules are dropped). */
    public static void setRules(ItemStack filter, List<ForestryFilterRule> rules) {
        Set<String> encoded = new LinkedHashSet<>();
        for (ForestryFilterRule rule : rules) {
            String value = rule.serialize();
            if (value != null) {
                encoded.add(value);
            }
            if (encoded.size() >= ForestryFilterRule.MAX_RULES) {
                break;
            }
        }

        setRuleStrings(filter, new ArrayList<>(encoded));
    }

    /**
     * Decides whether {@code testStack} passes the filter.
     *
     * <p>Only the rules whose species type matches the tested item are evaluated; rules for other species types
     * are ignored, which makes it possible to combine (for example) bee and tree conditions in one filter. An item
     * whose species type has no rules does not match, and an empty rule set matches nothing, so an allow list with
     * no rules rejects everything and a deny list with no rules accepts everything - the same convention as
     * LaserIO's own filters.
     */
    public static boolean matches(ItemStack filter, ItemStack testStack) {
        List<ForestryFilterRule> rules = getRules(filter);
        boolean matched = false;

        if (!rules.isEmpty()) {
            IGenome genome = ForestryGenetics.genome(testStack);
            if (genome != null) {
                matched = matchesAll(rules, genome);
            }
        }

        return matched == BaseFilter.getAllowList(filter);
    }

    /** @return whether every rule targeting the genome's species type matches it, and at least one such rule exists. */
    private static boolean matchesAll(List<ForestryFilterRule> rules, IGenome genome) {
        boolean hasRuleForSpeciesType = false;
        for (ForestryFilterRule rule : rules) {
            if (!rule.appliesTo(genome)) {
                continue;
            }

            hasRuleForSpeciesType = true;
            if (!rule.matches(genome)) {
                return false;
            }
        }

        return hasRuleForSpeciesType;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<ForestryFilterRule> rules = getRules(stack);
        boolean allowList = BaseFilter.getAllowList(stack);
        // Only spell out the species type when the filter mixes types; with a single type the chromosome names
        // already make the rule unambiguous.
        boolean multipleSpeciesTypes = rules.stream()
                .map(rule -> rule.speciesType().id())
                .distinct()
                .count() > 1;

        tooltip.add(Component.translatable(allowList
                        ? "tooltip.forestryio.forestry_filter.allowlist"
                        : "tooltip.forestryio.forestry_filter.denylist")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.forestryio.forestry_filter.rules", rules.size())
                .withStyle(ChatFormatting.GRAY));

        for (int i = 0; i < Math.min(rules.size(), TOOLTIP_RULE_LIMIT); i++) {
            ForestryFilterRule rule = rules.get(i);
            Component line = Component.literal(" " + rule.sideMarker() + " ");
            if (multipleSpeciesTypes) {
                line = line.copy().append(rule.speciesTypeName()).append(" ");
            }

            tooltip.add(line.copy()
                    .append(rule.chromosomeName())
                    .append(": ")
                    .append(rule.alleleName())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        if (rules.size() > TOOLTIP_RULE_LIMIT) {
            tooltip.add(Component.translatable("tooltip.forestryio.forestry_filter.more", rules.size() - TOOLTIP_RULE_LIMIT)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.translatable("tooltip.forestryio.forestry_filter.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
