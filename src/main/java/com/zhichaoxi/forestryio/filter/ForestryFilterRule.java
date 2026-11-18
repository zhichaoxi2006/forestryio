package com.zhichaoxi.forestryio.filter;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import forestry.api.core.genetics.IGenome;
import forestry.api.core.genetics.ISpeciesType;
import forestry.api.core.genetics.alleles.AllelePair;
import forestry.api.core.genetics.alleles.IChromosome;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One condition of the Forestry Filter: "the allele on side X of chromosome C of species type T has value V".
 *
 * <p>The rule deliberately stores the raw allele <em>value</em> instead of an {@code Allele}: dominance is a
 * property of the value and can be resolved again through the chromosome's reference resolver, so keeping only
 * the value makes the serialized form short and lets rules survive dominance changes without becoming invalid.
 *
 * <p>The value is encoded with the chromosome's own {@link IChromosome#valueCodec()} and stored as JSON inside a
 * plain string, which keeps the filter's data component a simple {@code List<String>} that works for every
 * chromosome (species ids, floats, enums, block positions, ...) without a hand written codec per type.
 *
 * <p>Rules used to be bee-only, so the serialized form starts with the chromosome id. It now starts with the
 * species type id ({@code forestry:tree_species|forestry:height|0|1.0}). {@link #parse(String)} still accepts the
 * old bee-only form so filters created before this change keep working.
 */
public record ForestryFilterRule(ISpeciesType<?, ?> speciesType, IChromosome<?> chromosome, boolean active,
                                 Object value) {
    /** Side marker used by Forestry itself: {@code 0} is the active allele, {@code 1} the inactive one. */
    public static final String ACTIVE_MARKER = "0";
    public static final String INACTIVE_MARKER = "1";

    private static final String SEPARATOR = "|";
    /**
     * Sanity cap so a malicious client cannot stuff a huge list into an item stack. The largest Forestry species
     * type has 14 chromosomes, so this is enough to combine the full attribute set of every species type.
     */
    public static final int MAX_RULES = 96;

    /** Parsed rules are immutable and only depend on the encoded string, so a small cache avoids re-parsing. */
    private static final int PARSE_CACHE_LIMIT = 512;
    private static final Map<String, ForestryFilterRule> PARSE_CACHE = new ConcurrentHashMap<>();

    /** @return whether the rule targets the same species type (and therefore karyotype) as the given genome. */
    public boolean appliesTo(IGenome genome) {
        return genome.getKaryotype().id().equals(speciesType.getKaryotype().id());
    }

    /**
     * @return whether the given genome carries this rule's allele on the requested side. Genomes of other species
     * types (bees, trees, butterflies) use different chromosomes or, when a chromosome object is shared, a
     * different karyotype, so they simply do not match.
     */
    public boolean matches(IGenome genome) {
        if (!appliesTo(genome) || !genome.getChromosomes().containsKey(chromosome)) {
            return false;
        }

        return Objects.equals(alleleValue(genome), value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object alleleValue(IGenome genome) {
        IChromosome raw = chromosome;
        return active ? genome.getActiveValue(raw) : genome.getInactiveValue(raw);
    }

    /** The species type name, e.g. "Bee" / "Tree" / "蜜蜂". */
    public Component speciesTypeName() {
        return speciesType.getDisplayName();
    }

    /** The chromosome name, e.g. "Species" / "品种". */
    public Component chromosomeName() {
        return Component.translatable(chromosome.chromosomeTranslationKey());
    }

    /**
     * The allele name, e.g. "Forest" / "森林". Forestry ships translations for its own chromosomes; if a
     * translation is missing (some locales lag behind) the raw value is shown instead of the translation key.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Component alleleName() {
        IChromosome raw = chromosome;
        String key = raw.translationKey(value);
        Component translated = Component.translatable(key);
        if (!net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            // Dedicated servers have no language data; the key is the best we can do there.
            return translated;
        }

        return translated.getString().equals(key) ? Component.literal(fallbackName(value)) : translated;
    }

    /** Human readable fallback for alleles without a translation. */
    private static String fallbackName(Object value) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }

        if (value instanceof ResourceLocation id) {
            return id.getPath();
        }

        return String.valueOf(value);
    }

    /** "0" for the active allele, "1" for the inactive one. */
    public String sideMarker() {
        return active ? ACTIVE_MARKER : INACTIVE_MARKER;
    }

    /**
     * Serializes the rule as {@code <species type>|<chromosome>|<side>|<json value>}. Returns {@code null} when the
     * chromosome's codec rejects the value, which should never happen for values that came from a genome.
     */
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String serialize() {
        Codec<Object> codec = (Codec) chromosome.valueCodec();
        DataResult<JsonElement> encoded = codec.encodeStart(JsonOps.INSTANCE, value);
        if (encoded.result().isEmpty()) {
            return null;
        }

        return speciesType.id() + SEPARATOR + chromosome.id() + SEPARATOR + sideMarker() + SEPARATOR
                + encoded.result().get();
    }

    /** Parses a serialized rule, returning {@code null} for anything that is not currently resolvable. */
    @Nullable
    public static ForestryFilterRule parse(@Nullable String encoded) {
        if (encoded == null) {
            return null;
        }

        ForestryFilterRule cached = PARSE_CACHE.get(encoded);
        if (cached != null) {
            return cached;
        }

        ForestryFilterRule parsed = parseUncached(encoded);
        if (parsed != null) {
            if (PARSE_CACHE.size() >= PARSE_CACHE_LIMIT) {
                PARSE_CACHE.clear();
            }
            PARSE_CACHE.put(encoded, parsed);
        }

        return parsed;
    }

    @Nullable
    private static ForestryFilterRule parseUncached(String encoded) {
        int first = encoded.indexOf(SEPARATOR);
        int second = first < 0 ? -1 : encoded.indexOf(SEPARATOR, first + 1);
        int third = second < 0 ? -1 : encoded.indexOf(SEPARATOR, second + 1);

        // New form: species type | chromosome | side | json. If the first field is a known species type and the
        // second field one of its chromosomes, this is a new rule and a broken value must not be reinterpreted.
        if (first > 0 && second > 0 && third > 0) {
            ISpeciesType<?, ?> speciesType = speciesTypeFrom(encoded.substring(0, first));
            if (speciesType != null) {
                IChromosome<?> chromosome = chromosomeOf(speciesType, encoded.substring(first + 1, second));
                String side = encoded.substring(second + 1, third);
                if (chromosome != null && (ACTIVE_MARKER.equals(side) || INACTIVE_MARKER.equals(side))) {
                    return decode(speciesType, chromosome, !INACTIVE_MARKER.equals(side), encoded.substring(third + 1));
                }
            }
        }

        return parseLegacyBeeRule(encoded);
    }

    /**
     * Reads the bee-only form written by earlier versions: {@code <chromosome>|<side>|<json value>}. Keeping this
     * path means an existing filter does not lose its conditions when the mod is updated.
     */
    @Nullable
    private static ForestryFilterRule parseLegacyBeeRule(String encoded) {
        int first = encoded.indexOf(SEPARATOR);
        int second = first < 0 ? -1 : encoded.indexOf(SEPARATOR, first + 1);
        if (first <= 0 || second < 0) {
            return null;
        }

        ISpeciesType<?, ?> beeType = ForestryGenetics.beeSpeciesType();
        if (beeType == null) {
            return null;
        }

        IChromosome<?> chromosome = chromosomeOf(beeType, encoded.substring(0, first));
        if (chromosome == null) {
            return null;
        }

        boolean active = !INACTIVE_MARKER.equals(encoded.substring(first + 1, second));
        return decode(beeType, chromosome, active, encoded.substring(second + 1));
    }

    @Nullable
    private static ISpeciesType<?, ?> speciesTypeFrom(String id) {
        ResourceLocation speciesTypeId;
        try {
            speciesTypeId = ResourceLocation.parse(id);
        } catch (RuntimeException exception) {
            return null;
        }

        return ForestryGenetics.speciesType(speciesTypeId);
    }

    @Nullable
    private static IChromosome<?> chromosomeOf(ISpeciesType<?, ?> speciesType, String id) {
        ResourceLocation chromosomeId;
        try {
            chromosomeId = ResourceLocation.parse(id);
            return speciesType.getKaryotype().getChromosome(chromosomeId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ForestryFilterRule decode(ISpeciesType<?, ?> speciesType, IChromosome<?> chromosome,
                                             boolean active, String encodedValue) {
        JsonElement json;
        try {
            json = JsonParser.parseString(encodedValue);
        } catch (RuntimeException exception) {
            return null;
        }

        Codec<Object> codec = (Codec) chromosome.valueCodec();
        return codec.parse(JsonOps.INSTANCE, json)
                .result()
                .map(decoded -> new ForestryFilterRule(speciesType, chromosome, active, decoded))
                .orElse(null);
    }

    /** Parses a list of serialized rules, skipping the ones that can no longer be resolved. */
    public static List<ForestryFilterRule> parseAll(List<String> encoded) {
        List<ForestryFilterRule> rules = new ArrayList<>(Math.min(encoded.size(), MAX_RULES));
        for (String entry : encoded) {
            if (rules.size() >= MAX_RULES) {
                break;
            }

            ForestryFilterRule rule = parse(entry);
            if (rule != null) {
                rules.add(rule);
            }
        }

        return rules;
    }

    /** Builds the active and inactive variants of a genome's allele pair, in karyotype order. */
    public static List<ForestryFilterRule> fromAllelePair(ISpeciesType<?, ?> speciesType, IChromosome<?> chromosome,
                                                          AllelePair<?> pair) {
        return List.of(
                new ForestryFilterRule(speciesType, chromosome, true, pair.active().value()),
                new ForestryFilterRule(speciesType, chromosome, false, pair.inactive().value()));
    }
}
