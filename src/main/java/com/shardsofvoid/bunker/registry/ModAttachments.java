package com.shardsofvoid.bunker.registry;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.shardsofvoid.bunker.BunkerMod;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Data attachments игрока (раздел 8.1). */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BunkerMod.MODID);

    /** Доза радиации. Перенос при смерти делается вручную (radiation.resetOnDeath). */
    public static final Supplier<AttachmentType<Double>> RADIATION_DOSE = ATTACHMENTS.register("radiation_dose",
            () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).build());

    /** Ядро уже выдано этому игроку. */
    public static final Supplier<AttachmentType<Boolean>> CORE_GIVEN = ATTACHMENTS.register("bunker_core_given",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    /** UUID бункера игрока строкой; пустая строка — бункера нет. */
    public static final Supplier<AttachmentType<String>> BUNKER_ID = ATTACHMENTS.register("bunker_id",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).copyOnDeath().build());

    /** Предметы ядра, сохранённые при смерти (bunker.core.keepOnDeath). */
    public static final Supplier<AttachmentType<List<ItemStack>>> KEPT_ITEMS = ATTACHMENTS.register("kept_items",
            () -> AttachmentType.<List<ItemStack>>builder(() -> List.of()).serialize(ItemStack.CODEC.listOf()).build());

    private ModAttachments() {}
}
