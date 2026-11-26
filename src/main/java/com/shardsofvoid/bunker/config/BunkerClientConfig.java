package com.shardsofvoid.bunker.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Клиентский конфиг: положение HUD и громкость счётчика. */
public final class BunkerClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<Anchor> HUD_ANCHOR;
    public static final ModConfigSpec.IntValue HUD_OFFSET_X;
    public static final ModConfigSpec.IntValue HUD_OFFSET_Y;
    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.BooleanValue HUD_AVOID_MINIMAP;
    public static final ModConfigSpec.IntValue HUD_MINIMAP_CLEARANCE;
    public static final ModConfigSpec.DoubleValue GEIGER_VOLUME;
    public static final ModConfigSpec.DoubleValue HIGHLIGHT_OPACITY;
    public static final ModConfigSpec.BooleanValue HIGHLIGHT_THROUGH_WALLS;

    public enum Anchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("hud");
        HUD_ENABLED = b.define("enabled", true);
        HUD_ANCHOR = b.comment("Угол экрана. По умолчанию справа сверху, чтобы не перекрывать HUD First Aid (он слева сверху).")
                .defineEnum("anchor", Anchor.TOP_RIGHT);
        HUD_OFFSET_X = b.defineInRange("offsetX", 4, 0, 4000);
        HUD_OFFSET_Y = b.comment("Сверху по умолчанию 54 — ниже двух рядов иконок эффектов.").defineInRange("offsetY", 54, 0, 4000);
        HUD_AVOID_MINIMAP = b.comment("В правом верхнем углу при установленной миникарте (FTB Chunks, Xaero, JourneyMap)",
                        "шкала опускается не выше minimapClearance, чтобы не залезать под карту.")
                .define("avoidMinimap", true);
        HUD_MINIMAP_CLEARANCE = b.comment("Отступ сверху под миникартой с подписями (координаты, биом), в пикселях интерфейса.")
                .defineInRange("minimapClearance", 96, 0, 4000);
        b.pop();
        b.push("geiger");
        GEIGER_VOLUME = b.comment("Громкость щелчков. Ещё её меняет ползунок «Игроки» в настройках звука.")
                .defineInRange("volume", 0.6, 0.0, 1.0);
        b.pop();
        b.push("highlight");
        HIGHLIGHT_OPACITY = b.comment("Непрозрачность подсветки (рамки и маркеры недостающих блоков), 0–1.")
                .defineInRange("opacity", 0.35, 0.0, 1.0);
        HIGHLIGHT_THROUGH_WALLS = b.comment("Маркеры недостающих блоков видны сквозь стены (раньше было всегда — и всё светилось красным).")
                .define("markersThroughWalls", false);
        b.pop();
        SPEC = b.build();
    }

    private BunkerClientConfig() {}
}
