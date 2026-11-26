package com.shardsofvoid.bunker.client;

import com.shardsofvoid.bunker.config.BunkerClientConfig;
import com.shardsofvoid.bunker.radiation.RadiationSource;
import com.shardsofvoid.bunker.radiation.RadiationSystem;
import com.shardsofvoid.bunker.registry.ModItems;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** HUD (раздел 1.5): шкала дозы, номер стадии, значок источника защиты. По умолчанию справа сверху, под иконками эффектов. */
public final class RadiationHud {
    private static final int WIDTH = 104;
    private static final int HEIGHT = 24;
    private static final int[] STAGE_COLORS = {0xFF55FF55, 0xFFFFFF55, 0xFFFFAA00, 0xFFFF5555, 0xFFAA0000};

    private static ItemStack suitIcon = ItemStack.EMPTY;
    private static ItemStack bunkerIcon = ItemStack.EMPTY;

    private RadiationHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClientState.hasData || !BunkerClientConfig.HUD_ENABLED.get()) return;

        boolean life = ClientState.air >= 0 || ClientState.unpowered;
        int height = HEIGHT + (life ? 11 : 0);
        int x = BunkerClientConfig.HUD_OFFSET_X.get();
        int y = BunkerClientConfig.HUD_OFFSET_Y.get();
        switch (BunkerClientConfig.HUD_ANCHOR.get()) {
            case TOP_RIGHT -> {
                x = g.guiWidth() - WIDTH - x;
                if (BunkerClientConfig.HUD_AVOID_MINIMAP.get() && MinimapCompat.present()) {
                    y = Math.max(y, BunkerClientConfig.HUD_MINIMAP_CLEARANCE.get());
                }
            }
            case BOTTOM_LEFT -> y = g.guiHeight() - height - y;
            case BOTTOM_RIGHT -> {
                x = g.guiWidth() - WIDTH - x;
                y = g.guiHeight() - height - y;
            }
            default -> {
            }
        }

        Font font = mc.font;
        int stage = Math.max(0, Math.min(4, ClientState.stage));
        int color = STAGE_COLORS[stage];

        g.fill(x, y, x + WIDTH, y + height, 0x90000000);
        if (life) renderLife(g, font, mc, x + 3, y + HEIGHT);

        // значок источника защиты
        ItemStack icon = switch (ClientState.source) {
            case SUIT -> suitIcon();
            case BUNKER -> bunkerIcon();
            default -> ItemStack.EMPTY;
        };
        int textX = x + 3;
        if (!icon.isEmpty()) {
            g.renderItem(icon, x + 3, y + 4);
            textX = x + 22;
        }

        Component title = Component.literal("☢ ").append(Component.translatable("hud.bunker.stage", RadiationSystem.roman(stage)));
        g.drawString(font, title, textX, y + 3, color, true);

        String doseText = String.format("%.0f/%.0f", ClientState.dose, ClientState.maxDose);
        g.drawString(font, doseText, x + WIDTH - 3 - font.width(doseText), y + 3, 0xFFDDDDDD, true);

        // шкала дозы
        int barX = textX;
        int barW = x + WIDTH - 3 - barX;
        int barY = y + 15;
        double fraction = ClientState.maxDose <= 0 ? 0 : Math.min(1.0, ClientState.dose / ClientState.maxDose);
        g.fill(barX, barY, barX + barW, barY + 5, 0xFF303030);
        g.fill(barX, barY, barX + (int) Math.round(barW * fraction), barY + 5, color);

        if (ClientState.source == RadiationSource.BUNKER) {
            g.fill(barX, barY - 1, barX + barW, barY, 0xFF55AAFF);
        }
    }

    /** v3 8.7: значок воздуха, мигает ниже 25%; «Обесточено» в отключённой или обесточенной комнате. */
    private static void renderLife(GuiGraphics g, Font font, Minecraft mc, int x, int y) {
        boolean blinkOff = mc.level != null && (mc.level.getGameTime() / 10) % 2 == 0;
        int cx = x;
        if (ClientState.air >= 0) {
            int air = ClientState.air;
            if (!(air < 25 && blinkOff)) {
                Component t = Component.translatable("hud.bunker.air", air);
                g.drawString(font, t, cx, y, air < 25 ? 0xFFFF5555 : air < 50 ? 0xFFFFAA00 : 0xFFAADDFF, true);
            }
        } else if (ClientState.unpowered) {
            g.drawString(font, Component.translatable("hud.bunker.unpowered"), cx, y, 0xFFFF5555, true);
        }
        if (ClientState.unpowered && ClientState.air >= 0 && !blinkOff) {
            g.fill(x - 3, y - 1, x - 1, y + 8, 0xFFFF5555);
        }
    }

    private static ItemStack suitIcon() {
        if (suitIcon.isEmpty()) suitIcon = new ItemStack(ModItems.RADSUIT_HELMET.get());
        return suitIcon;
    }

    private static ItemStack bunkerIcon() {
        if (bunkerIcon.isEmpty()) bunkerIcon = new ItemStack(ModItems.BUNKER_CORE.get());
        return bunkerIcon;
    }
}
