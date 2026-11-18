package com.zhichaoxi.forestryio.filter;

import forestry.api.IForestryApi;
import forestry.api.core.genetics.ForestrySpeciesTypes;
import forestry.api.core.genetics.IGenome;
import forestry.api.core.genetics.ISpeciesType;
import forestry.api.core.genetics.alleles.AllelePair;
import forestry.api.core.genetics.alleles.IChromosome;
import forestry.api.core.genetics.capability.IIndividualHandlerItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge between the filter and Forestry's genetics API.
 *
 * <p>Every lookup goes through the public {@link IForestryApi} instead of Forestry's internal classes, so the
 * filter keeps working across Forestry: Community Edition updates.
 *
 * <p>The filter used to be bee-only. It now works with every genetic species type Forestry registers (this
 * Forestry version registers bees and trees; butterflies or other types work automatically once a version or addon
 * registers them), so the helper methods no longer hard-code the bee karyotype: the species type is derived from
 * the genome/item and all chromosome lookups go through that type's karyotype.
 */
public final class ForestryGenetics {
    private ForestryGenetics() {
    }

    /** Resolves a registered species type by its id ({@code forestry:bee_species}, {@code forestry:tree_species}, ...). */
    @Nullable
    public static ISpeciesType<?, ?> speciesType(ResourceLocation id) {
        return IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypeSafe(id);
    }

    /**
     * Finds the species type that owns the given genome by matching the genome's karyotype.
     *
     * <p>Genomes only carry their karyotype, not the species type itself, and a karyotype id is the id Forestry
     * registered the species type with.
     */
    @Nullable
    public static ISpeciesType<?, ?> speciesType(IGenome genome) {
        ResourceLocation karyotypeId = genome.getKaryotype().id();
        for (ISpeciesType<?, ?> type : IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypes()) {
            if (type.getKaryotype().id().equals(karyotypeId)) {
                return type;
            }
        }

        return null;
    }

    /**
     * The species type of an item stack, or {@code null} when the item carries no genome. Forestry's own genetic
     * items know their type without inspecting the genome; items that only carry a genome component fall back to
     * the karyotype lookup.
     */
    @Nullable
    public static ISpeciesType<?, ?> speciesType(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        ISpeciesType<?, ?> type = IIndividualHandlerItem.getSpeciesType(stack);
        if (type != null) {
            return type;
        }

        IGenome genome = genome(stack);
        return genome == null ? null : speciesType(genome);
    }

    /** The genome stored on an item stack, or {@code null} for items that are not genetic individuals. */
    @Nullable
    public static IGenome genome(ItemStack stack) {
        return stack.isEmpty() ? null : IIndividualHandlerItem.getGenome(stack);
    }

    /** @return whether the stack is a Forestry individual (bee, sapling, pollen, butterfly, ...) with a genome. */
    public static boolean isIndividual(ItemStack stack) {
        return speciesType(stack) != null && genome(stack) != null;
    }

    /** The bee species type, used when reading rules written by the bee-only version of the filter. */
    @Nullable
    public static ISpeciesType<?, ?> beeSpeciesType() {
        return speciesType(ForestrySpeciesTypes.BEE);
    }

    /**
     * Lists every filterable allele of the given genome: for each chromosome the active allele followed by the
     * inactive one, in karyotype order. Genomes without a registered species type yield an empty list.
     */
    public static List<ForestryFilterRule> rulesFor(IGenome genome) {
        ISpeciesType<?, ?> type = speciesType(genome);
        if (type == null) {
            return List.of();
        }

        List<ForestryFilterRule> rules = new ArrayList<>(type.getKaryotype().size() * 2);
        for (IChromosome<?> chromosome : type.getKaryotype().getChromosomes()) {
            AllelePair<?> pair = genome.getAllelePair(chromosome);
            if (pair != null) {
                rules.addAll(ForestryFilterRule.fromAllelePair(type, chromosome, pair));
            }
        }

        return rules;
    }
}
