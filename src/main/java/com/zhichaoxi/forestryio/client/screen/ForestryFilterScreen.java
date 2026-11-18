package com.zhichaoxi.forestryio.client.screen;

import com.direwolf20.laserio.client.screens.widgets.IconButton;
import com.direwolf20.laserio.client.screens.widgets.ToggleButton;
import com.direwolf20.laserio.common.containers.customslot.FilterBasicSlot;
import com.direwolf20.laserio.common.items.filters.BaseFilter;
import com.direwolf20.laserio.common.network.data.GhostSlotPayload;
import com.zhichaoxi.forestryio.filter.ForestryFilterRule;
import com.zhichaoxi.forestryio.filter.ForestryGenetics;
import com.zhichaoxi.forestryio.item.ForestryFilterItem;
import com.zhichaoxi.forestryio.menu.ForestryFilterMenu;
import com.zhichaoxi.forestryio.network.UpdateForestryFilterPayload;
import forestry.api.core.genetics.IGenome;
import forestry.api.core.genetics.ISpeciesType;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Configuration screen of the Forestry Filter.
 *
 * <p>The layout is LaserIO's data filter screen (same texture, same button positions, same list geometry) so the
 * filter feels like a native LaserIO filter. Instead of data component names the list shows the chromosomes of the
 * sample's species type (bee, tree, butterfly, ...): one row for the active allele and one for the inactive allele.
 * Clicking a row adds or removes that condition; every condition for a stack's species type has to match for the
 * stack to pass (AND), and the allow/deny toggle inverts the result.
 *
 * <p>Conditions that are already part of the filter are always part of the list, even when the current sample cannot
 * offer them: with no sample, with a sample of another species type, or when the sample carries a different allele
 * value on that chromosome. They are inserted next to the rows of the same chromosome (or after the sample's rows
 * when their species type is not the sample's) and get the species type in front, so a configured condition can
 * never become invisible and impossible to remove.
 */
public class ForestryFilterScreen extends AbstractContainerScreen<ForestryFilterMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("laserio", "textures/gui/filtertag.png");
    private static final ResourceLocation ALLOW_FALSE =
            ResourceLocation.fromNamespaceAndPath("laserio", "textures/gui/buttons/allowlistfalse.png");
    private static final ResourceLocation ALLOW_TRUE =
            ResourceLocation.fromNamespaceAndPath("laserio", "textures/gui/buttons/allowlisttrue.png");
    private static final ResourceLocation ADD_BUTTON =
            ResourceLocation.fromNamespaceAndPath("laserio", "textures/gui/buttons/add.png");
    private static final ResourceLocation REMOVE_BUTTON =
            ResourceLocation.fromNamespaceAndPath("laserio", "textures/gui/buttons/remove.png");
    private static final ResourceLocation CLEAR_BUTTON =
            ResourceLocation.fromNamespaceAndPath("laserio", "textures/gui/buttons/clear.png");
    private static final ResourceLocation PAGE_UP_BUTTON =
            ResourceLocation.fromNamespaceAndPath("laserio", "textures/gui/buttons/pageup.png");
    private static final ResourceLocation PAGE_DOWN_BUTTON =
            ResourceLocation.fromNamespaceAndPath("laserio", "textures/gui/buttons/pagedown.png");

    /** Screen geometry, copied from LaserIO's data filter so the shared texture lines up. */
    private static final int LIST_X = 7;
    private static final int LIST_Y = 47;
    private static final int LIST_WIDTH = 162;
    private static final int ROW_HEIGHT = 10;
    private static final int ROWS_PER_PAGE = 11;
    private static final int ROW_TEXT_WIDTH = 152;

    private static final int COLOR_DARK_GRAY = 0xFF404040;
    private static final int COLOR_SELECTED = 0xFF0000FF;
    private static final int COLOR_HOVER = 0x80FFFFFF;
    private static final int COLOR_BORDER = 0xFFFF0000;

    private final ForestryFilterMenu filterMenu;
    private final List<ForestryFilterRule> selectedRules = new ArrayList<>();

    private EditBox searchField;
    private boolean allowList;
    private int page;
    private int maxPages;
    private int hoveredRow = -1;
    private int selectedRow = -1;
    private List<ForestryFilterRule> visibleRules = List.of();
    /** Species type of the current sample, used to decide which rows need the species type in front. */
    private ISpeciesType<?, ?> sampleSpeciesType;

    public ForestryFilterScreen(ForestryFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.filterMenu = menu;
        this.imageWidth = 200;
        this.imageHeight = 254;
        this.allowList = BaseFilter.getAllowList(menu.filterItem);
        this.selectedRules.addAll(ForestryFilterItem.getRules(menu.filterItem));
    }

    @Override
    protected void init() {
        super.init();

        List<AbstractWidget> widgets = new ArrayList<>();
        widgets.add(new ToggleButton(this.getGuiLeft() + 5, this.getGuiTop() + 5, 16, 16,
                new ResourceLocation[]{ALLOW_FALSE, ALLOW_TRUE}, this.allowList ? 1 : 0, button -> {
            this.allowList = !this.allowList;
            ((ToggleButton) button).setTexturePosition(this.allowList ? 1 : 0);
            this.sendUpdate();
        }));
        widgets.add(new IconButton(this.getGuiLeft() + 155, this.getGuiTop() + 5, 16, 16, ADD_BUTTON,
                button -> this.addAllSampleRules()));
        widgets.add(new IconButton(this.getGuiLeft() + 135, this.getGuiTop() + 5, 16, 16, REMOVE_BUTTON,
                button -> this.removeSelectedRule()));
        widgets.add(new IconButton(this.getGuiLeft() + 115, this.getGuiTop() + 5, 16, 16, CLEAR_BUTTON,
                button -> {
                    this.selectedRules.clear();
                    this.sendUpdate();
                }));
        widgets.add(new IconButton(this.getGuiLeft() + 100, this.getGuiTop() + 157, 12, 12, PAGE_UP_BUTTON,
                button -> this.page = Math.min(this.page + 1, this.maxPages)));
        widgets.add(new IconButton(this.getGuiLeft() + 58, this.getGuiTop() + 157, 12, 12, PAGE_DOWN_BUTTON,
                button -> this.page = Math.max(this.page - 1, 0)));

        this.searchField = new EditBox(this.font, this.getGuiLeft() + 7, this.getGuiTop() + 25, 160, 15, Component.empty());
        this.searchField.setMaxLength(60);
        this.searchField.setHint(Component.translatable("gui.forestryio.forestry_filter.search"));
        widgets.add(this.searchField);

        widgets.forEach(this::addRenderableWidget);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.rebuildVisibleRules();
        this.renderRuleList(guiGraphics, mouseX, mouseY);
        this.renderPageLabel(guiGraphics);
        this.renderTooltips(guiGraphics, mouseX, mouseY);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /** Recomputes the rows shown on the current page from the sample in the ghost slot and the selected rules. */
    private void rebuildVisibleRules() {
        ItemStack sample = this.filterMenu.handler.getStackInSlot(ForestryFilterMenu.SAMPLE_SLOT);
        IGenome genome = ForestryGenetics.genome(sample);
        this.sampleSpeciesType = ForestryGenetics.speciesType(sample);

        List<ForestryFilterRule> allRules = new ArrayList<>();
        if (genome != null) {
            allRules.addAll(ForestryGenetics.rulesFor(genome));
        }

        // Selected conditions stay visible even when the sample cannot offer them, otherwise they would keep
        // filtering without any way to see or remove them.
        for (ForestryFilterRule rule : this.selectedRules) {
            if (!allRules.contains(rule)) {
                addSelectedRule(allRules, rule);
            }
        }

        String query = this.searchField == null ? "" : this.searchField.getValue().trim().toLowerCase(Locale.ROOT);

        if (!query.isEmpty()) {
            allRules = allRules.stream().filter(rule -> this.rowText(rule).toLowerCase(Locale.ROOT).contains(query)).toList();
        }

        this.maxPages = Math.max((int) Math.ceil(allRules.size() / (double) ROWS_PER_PAGE) - 1, 0);
        this.page = Mth.clamp(this.page, 0, this.maxPages);
        int from = Math.min(this.page * ROWS_PER_PAGE, allRules.size());
        int to = Math.min(from + ROWS_PER_PAGE, allRules.size());
        this.visibleRules = allRules.subList(from, to);
    }

    /**
     * Adds a selected rule that the sample does not provide. Rules for a chromosome that is already listed are
     * inserted after that chromosome's rows so they stay on the same page as the rest of the chromosome; rules for
     * a species type the sample does not belong to are appended at the end.
     */
    private static void addSelectedRule(List<ForestryFilterRule> rules, ForestryFilterRule rule) {
        int insertAt = -1;
        for (int index = 0; index < rules.size(); index++) {
            ForestryFilterRule listed = rules.get(index);
            if (listed.speciesType().id().equals(rule.speciesType().id())
                    && listed.chromosome().id().equals(rule.chromosome().id())) {
                insertAt = index + 1;
            }
        }

        if (insertAt >= 0) {
            rules.add(insertAt, rule);
        } else {
            rules.add(rule);
        }
    }

    private void renderRuleList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int listLeft = this.getGuiLeft() + LIST_X;
        int listTop = this.getGuiTop() + LIST_Y;

        guiGraphics.fillGradient(listLeft - 2, listTop - 4, listLeft + LIST_WIDTH, listTop + 110, 0x88505050, 0x88505050);

        this.hoveredRow = -1;
        if (this.visibleRules.isEmpty()) {
            ItemStack sample = this.filterMenu.handler.getStackInSlot(ForestryFilterMenu.SAMPLE_SLOT);
            boolean searching = this.searchField != null && !this.searchField.getValue().trim().isEmpty();
            Component empty = Component.translatable(searching || ForestryGenetics.isIndividual(sample)
                    ? "gui.forestryio.forestry_filter.no_matches"
                    : "gui.forestryio.forestry_filter.no_sample");
            this.renderEmptyHint(guiGraphics, empty, listLeft, listTop);
            return;
        }

        for (int index = 0; index < this.visibleRules.size(); index++) {
            ForestryFilterRule rule = this.visibleRules.get(index);
            int rowTop = listTop + index * ROW_HEIGHT;
            String text = this.rowText(rule);
            int color = this.selectedRules.contains(rule) ? COLOR_SELECTED : COLOR_DARK_GRAY;

            if (inBounds(listLeft, rowTop - 2, LIST_WIDTH, ROW_HEIGHT - 2, mouseX, mouseY)) {
                this.hoveredRow = index;
                guiGraphics.fillGradient(listLeft - 1, rowTop - 2, listLeft + LIST_WIDTH - 1, rowTop + 8, COLOR_HOVER, COLOR_HOVER);
            }

            renderScrollingString(guiGraphics, this.font, Component.literal(text), listLeft, rowTop, listLeft + ROW_TEXT_WIDTH, color);

            if (index == this.selectedRow) {
                int right = listLeft + LIST_WIDTH - 2;
                int bottom = rowTop + 10;
                guiGraphics.hLine(listLeft - 2, right, rowTop - 2, COLOR_BORDER);
                guiGraphics.hLine(listLeft - 2, right, bottom - 3, COLOR_BORDER);
                guiGraphics.vLine(listLeft - 2, rowTop - 2, bottom - 2, COLOR_BORDER);
                guiGraphics.vLine(right, rowTop - 2, bottom - 2, COLOR_BORDER);
            }
        }
    }

    /**
     * Draws the empty-list hint centered inside the list area. Long translations (for example the English
     * "no sample" text) are wrapped at word boundaries instead of running past the filter's frame.
     */
    private void renderEmptyHint(GuiGraphics guiGraphics, Component hint, int listLeft, int listTop) {
        int maxWidth = LIST_WIDTH - 4;
        int y = listTop + 2;
        for (FormattedCharSequence line : this.font.split(hint, maxWidth)) {
            int x = listLeft + 2 + (maxWidth - this.font.width(line)) / 2;
            guiGraphics.drawString(this.font, line, x, y, COLOR_DARK_GRAY, false);
            y += ROW_HEIGHT;
        }
    }

    private void renderPageLabel(GuiGraphics guiGraphics) {
        String label = (this.page + 1) + " / " + (this.maxPages + 1);
        int x = this.getGuiLeft() + 87 - this.font.width(label) / 2;
        guiGraphics.drawString(this.font, label, x, this.getGuiTop() + 160, COLOR_DARK_GRAY, false);
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (inBounds(this.getGuiLeft() + 5, this.getGuiTop() + 5, 16, 16, mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.translatable(this.allowList
                    ? "screen.laserio.allowlist"
                    : "screen.laserio.denylist"), mouseX, mouseY);
        } else if (inBounds(this.getGuiLeft() + 155, this.getGuiTop() + 5, 16, 16, mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.translatable("gui.forestryio.forestry_filter.add_all"), mouseX, mouseY);
        } else if (inBounds(this.getGuiLeft() + 135, this.getGuiTop() + 5, 16, 16, mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.translatable("gui.forestryio.forestry_filter.remove_selected"), mouseX, mouseY);
        } else if (inBounds(this.getGuiLeft() + 115, this.getGuiTop() + 5, 16, 16, mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.translatable("gui.forestryio.forestry_filter.clear_all"), mouseX, mouseY);
        } else if (this.hoveredRow >= 0 && this.hoveredRow < this.visibleRules.size()) {
            ForestryFilterRule rule = this.visibleRules.get(this.hoveredRow);
            guiGraphics.renderTooltip(this.font,
                    List.of(this.rowComponent(rule), Component.translatable("gui.forestryio.forestry_filter.row_hint")),
                    java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private String rowText(ForestryFilterRule rule) {
        String text = rule.sideMarker() + " " + rule.chromosomeName().getString() + ": " + rule.alleleName().getString();
        return this.showSpeciesType(rule) ? rule.speciesTypeName().getString() + " " + text : text;
    }

    private Component rowComponent(ForestryFilterRule rule) {
        Component text = Component.literal(rule.sideMarker() + " ")
                .append(rule.chromosomeName())
                .append(": ")
                .append(rule.alleleName());
        if (!this.showSpeciesType(rule)) {
            return text;
        }

        return rule.speciesTypeName().copy().append(" ").append(text);
    }

    /** Rows of another species type (or every row when there is no sample) are prefixed with the species type. */
    private boolean showSpeciesType(ForestryFilterRule rule) {
        return this.sampleSpeciesType == null
                || !this.sampleSpeciesType.getKaryotype().id().equals(rule.speciesType().getKaryotype().id());
    }

    private void addAllSampleRules() {
        ItemStack sample = this.filterMenu.handler.getStackInSlot(ForestryFilterMenu.SAMPLE_SLOT);
        IGenome genome = ForestryGenetics.genome(sample);
        if (genome == null) {
            return;
        }

        for (ForestryFilterRule rule : ForestryGenetics.rulesFor(genome)) {
            if (!this.selectedRules.contains(rule)) {
                this.selectedRules.add(rule);
            }
            if (this.selectedRules.size() >= ForestryFilterRule.MAX_RULES) {
                break;
            }
        }

        this.sendUpdate();
    }

    private void removeSelectedRule() {
        if (this.selectedRow >= 0 && this.selectedRow < this.visibleRules.size()) {
            this.selectedRules.remove(this.visibleRules.get(this.selectedRow));
            this.selectedRow = -1;
            this.sendUpdate();
        }
    }

    private void toggleRule(ForestryFilterRule rule) {
        if (!this.selectedRules.remove(rule) && this.selectedRules.size() < ForestryFilterRule.MAX_RULES) {
            this.selectedRules.add(rule);
        }

        this.sendUpdate();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.hoveredRow >= 0 && this.hoveredRow < this.visibleRules.size()) {
            ForestryFilterRule rule = this.visibleRules.get(this.hoveredRow);
            if (button == 0) {
                this.toggleRule(rule);
            } else if (button == 1) {
                this.selectedRules.remove(rule);
                this.sendUpdate();
            }

            this.selectedRow = this.hoveredRow;
            return true;
        }

        if (this.hoveredSlot instanceof FilterBasicSlot) {
            ItemStack carried = this.menu.getCarried();
            if (carried.isEmpty()) {
                // Right-click (or click with an empty hand) clears the sample.
                this.hoveredSlot.set(ItemStack.EMPTY);
                PacketDistributor.sendToServer(new GhostSlotPayload(ForestryFilterMenu.SAMPLE_SLOT, ItemStack.EMPTY, 0, -1));
                return true;
            }

            if (!ForestryGenetics.isIndividual(carried)) {
                return true;
            }

            ItemStack sample = carried.copyWithCount(1);
            this.hoveredSlot.set(sample);
            PacketDistributor.sendToServer(new GhostSlotPayload(ForestryFilterMenu.SAMPLE_SLOT, sample, 1, -1));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.hoveredSlot == null && scrollY != 0.0) {
            this.page = Mth.clamp(this.page + (scrollY < 0.0 ? 1 : -1), 0, this.maxPages);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            if (this.searchField != null && this.searchField.isFocused()) {
                this.searchField.setFocused(false);
                return true;
            }

            this.onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.sendUpdate();
        super.onClose();
    }

    /**
     * Pushes the current configuration to the server. Sending on every change (not just on close) keeps the item
     * in sync even if the container is torn down before the close packet is processed.
     */
    private void sendUpdate() {
        PacketDistributor.sendToServer(new UpdateForestryFilterPayload(this.allowList, this.encodedRules()));
    }

    private List<String> encodedRules() {
        List<String> encoded = new ArrayList<>(this.selectedRules.size());
        for (ForestryFilterRule rule : this.selectedRules) {
            String value = rule.serialize();
            if (value != null) {
                encoded.add(value);
            }
        }

        return encoded;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, this.getGuiLeft(), this.getGuiTop(), 0.0F, 0.0F, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // The data filter texture has no title/inventory labels; keep the screen clean.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Clips and horizontally scrolls a string that is wider than the list, like LaserIO's data filter does. */
    private static void renderScrollingString(GuiGraphics guiGraphics, Font font, Component text,
                                              int xStart, int yStart, int xEnd, int color) {
        int textWidth = font.width(text);
        int maxWidth = xEnd - xStart;
        if (textWidth <= maxWidth) {
            guiGraphics.drawString(font, text, xStart, yStart, color, false);
            return;
        }

        int overflow = textWidth - maxWidth;
        double seconds = (double) Util.getMillis() / 1000.0;
        double duration = Math.max((double) overflow * 0.5, 3.0);
        double oscillation = Math.sin((Math.PI / 2) * Math.cos((Math.PI * 2) * seconds / duration)) / 2.0 + 0.5;
        double offset = Mth.lerp(oscillation, 0.0, (double) overflow);
        guiGraphics.enableScissor(xStart, yStart, xEnd, yStart + 9);
        guiGraphics.drawString(font, text, xStart - (int) offset, yStart, color, false);
        guiGraphics.disableScissor();
    }

    private static boolean inBounds(double x, double y, double width, double height, double pointX, double pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }
}
