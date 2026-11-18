package com.zhichaoxi.forestryio.network;

import com.zhichaoxi.forestryio.ForestryIO;
import com.zhichaoxi.forestryio.menu.ForestryFilterMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent when the Forestry Filter screen closes: the current allow/deny flag and the serialized conditions.
 *
 * <p>The server re-parses every entry before storing it, so a modified client cannot inject rules that do not
 * resolve to a real chromosome/allele of the current Forestry version.
 */
public record UpdateForestryFilterPayload(boolean allowList, List<String> rules) implements CustomPacketPayload {
    public static final Type<UpdateForestryFilterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ForestryIO.MOD_ID, "update_forestry_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateForestryFilterPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    UpdateForestryFilterPayload::allowList,
                    ByteBufCodecs.<FriendlyByteBuf, String, List<String>>collection(ArrayList::new, ByteBufCodecs.STRING_UTF8),
                    UpdateForestryFilterPayload::rules,
                    UpdateForestryFilterPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateForestryFilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ForestryFilterMenu menu) {
                menu.applyClientRules(payload.allowList(), payload.rules());
            }
        });
    }
}
