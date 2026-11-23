package com.shardsofvoid.bunker.registry;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.shardsofvoid.bunker.BunkerMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data components предметов (раздел 8.1). */
public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BunkerMod.MODID);

    /** Уровень части костюма, 1–3. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RADSUIT_LEVEL =
            COMPONENTS.registerComponentType("radsuit_level", b -> b
                    .persistent(Codec.intRange(1, 3))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Привязка блока комнат к ядру. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LinkedCore>> LINKED_CORE =
            COMPONENTS.registerComponentType("linked_core", b -> b
                    .persistent(LinkedCore.CODEC)
                    .networkSynchronized(LinkedCore.STREAM_CODEC));

    /** Владелец предмета ядра. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CoreOwner>> CORE_OWNER =
            COMPONENTS.registerComponentType("core_owner", b -> b
                    .persistent(CoreOwner.CODEC)
                    .networkSynchronized(CoreOwner.STREAM_CODEC));

    /** Уровень блока комнат, сохраняется при снятии блока владельцем. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ROOM_LEVEL =
            COMPONENTS.registerComponentType("room_level", b -> b
                    .persistent(Codec.intRange(1, 3))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModComponents() {}

    public record LinkedCore(ResourceKey<Level> dimension, BlockPos pos, UUID bunkerId) {
        public static final Codec<LinkedCore> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(LinkedCore::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(LinkedCore::pos),
                UUIDUtil.CODEC.fieldOf("bunker").forGetter(LinkedCore::bunkerId)
        ).apply(i, LinkedCore::new));

        public static final StreamCodec<ByteBuf, LinkedCore> STREAM_CODEC = StreamCodec.composite(
                ResourceKey.streamCodec(Registries.DIMENSION), LinkedCore::dimension,
                BlockPos.STREAM_CODEC, LinkedCore::pos,
                UUIDUtil.STREAM_CODEC, LinkedCore::bunkerId,
                LinkedCore::new);
    }

    public record CoreOwner(UUID id, String name) {
        public static final Codec<CoreOwner> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(CoreOwner::id),
                Codec.STRING.optionalFieldOf("name", "").forGetter(CoreOwner::name)
        ).apply(i, CoreOwner::new));

        public static final StreamCodec<ByteBuf, CoreOwner> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, CoreOwner::id,
                ByteBufCodecs.STRING_UTF8, CoreOwner::name,
                CoreOwner::new);
    }
}
