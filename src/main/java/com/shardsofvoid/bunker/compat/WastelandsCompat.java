package com.shardsofvoid.bunker.compat;

import com.shardsofvoid.bunker.BunkerMod;
import com.shardsofvoid.bunker.config.BunkerConfig;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Связка с Wastelands. Собственное начисление Wastelands выключает миксин
 * {@code WastelandsRadiationMixin}, а его шкала 0–100 (attachment wastelands:radiation)
 * становится зеркалом нашей дозы. Если что-то в Wastelands меняет шкалу (Rad-Away, команды),
 * разница переносится в нашу дозу при следующей проверке — так лечение Wastelands работает без правки его кода.
 */
public final class WastelandsCompat {
    public static final String MODID = "wastelands";
    private static final ResourceLocation ATTACHMENT_ID = ResourceLocation.fromNamespaceAndPath(MODID, "radiation");
    private static final int WASTELANDS_MAX = 100;

    private static Boolean loaded;
    private static AttachmentType<Integer> attachment;
    private static boolean resolveFailed;

    private WastelandsCompat() {}

    public static boolean isLoaded() {
        if (loaded == null) loaded = ModList.get().isLoaded(MODID);
        return loaded;
    }

    public static boolean active() {
        return isLoaded() && BunkerConfig.WASTELANDS_OVERRIDE.get() && resolve() != null;
    }

    @SuppressWarnings("unchecked")
    private static AttachmentType<Integer> resolve() {
        if (attachment == null && !resolveFailed) {
            AttachmentType<?> type = NeoForgeRegistries.ATTACHMENT_TYPES.get(ATTACHMENT_ID);
            if (type == null) {
                resolveFailed = true;
                BunkerMod.LOGGER.warn("Wastelands загружен, но attachment {} не найден — зеркалирование дозы выключено", ATTACHMENT_ID);
            } else {
                attachment = (AttachmentType<Integer>) type;
            }
        }
        return attachment;
    }

    /**
     * Перенести внешние изменения шкалы Wastelands в нашу дозу.
     *
     * @param lastMirror значение, которое мы записали в прошлый раз, или −1, если его ещё нет
     */
    public static double pullExternalChange(ServerPlayer player, int lastMirror, double dose, double maxDose) {
        if (!active() || lastMirror < 0) return dose;
        Integer current = player.getData(attachment);
        int value = current == null ? 0 : current;
        if (value == lastMirror) return dose;
        return dose + (value - lastMirror) * maxDose / WASTELANDS_MAX;
    }

    /** Записать нашу дозу в шкалу Wastelands. Возвращает записанное значение (или −1, если связки нет). */
    public static int push(ServerPlayer player, double dose, double maxDose) {
        if (!active()) return -1;
        int value = Mth.clamp((int) Math.floor(dose / maxDose * WASTELANDS_MAX + 1e-6), 0, WASTELANDS_MAX);
        Integer current = player.getData(attachment);
        if (current == null || current != value) player.setData(attachment, value);
        return value;
    }
}
