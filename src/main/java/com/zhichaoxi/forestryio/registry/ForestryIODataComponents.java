package com.zhichaoxi.forestryio.registry;

import com.mojang.serialization.Codec;
import com.zhichaoxi.forestryio.ForestryIO;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/** Data components added by Forestry IO. */
public final class ForestryIODataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ForestryIO.MOD_ID);

    /**
     * The serialized conditions of a Forestry Filter (see {@code ForestryFilterRule#serialize()}).
     *
     * <p>Stored as plain strings so the component has one codec for every species type and chromosome type; an
     * absent component means "no conditions", which (like every LaserIO filter) matches nothing in allow mode.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> FILTER_RULES =
            COMPONENTS.registerComponentType("filter_rules", builder -> builder
                    .persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.<FriendlyByteBuf, String, List<String>>collection(
                            ArrayList::new, ByteBufCodecs.STRING_UTF8)));

    private ForestryIODataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
