package com.shardsofvoid.bunker.client;

import com.shardsofvoid.bunker.energy.MachineKind;
import com.shardsofvoid.bunker.energy.MachineStatus;
import com.shardsofvoid.bunker.energy.WaterPurifierBlockEntity;
import com.shardsofvoid.bunker.menu.MachineMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Экран энергоблока с инвентарём (v3 4–8): генератор, стойка ячеек, станция костюмов, ФВУ, водоочистка.
 * Фон рисуется заливками, без текстур; числа приходят пакетом MachineDataPayload.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int BG = 0xFFC6C6C6;
    private static final int DARK = 0xFF373737;
    private static final int SLOT = 0xFF8B8B8B;

    private Button modeButton;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.kind == MachineKind.CELL_RACK) {
            modeButton = Button.builder(Component.empty(), b -> {
                if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }).bounds(leftPos + 70, topPos + 58, 98, 18).build();
            addRenderableWidget(modeButton);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (modeButton != null) {
            String[] modes = {"auto", "charge", "discharge"};
            int m = Math.max(0, Math.min(2, menu.value(4)));
            modeButton.setMessage(Component.translatable("gui.bunker.rack.mode." + modes[m]));
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        g.fill(x, y, x + imageWidth, y + 1, 0xFFFFFFFF);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF555555);
        for (Slot s : menu.slots) {
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, DARK);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, SLOT);
        }
        switch (menu.kind) {
            case FURNACE_GENERATOR -> {
                int total = menu.value(3);
                int left = menu.value(2);
                int h = total <= 0 ? 0 : Math.round(14f * left / total);
                g.fill(x + 26, y + 18, x + 40, y + 32, DARK);
                if (h > 0) g.fill(x + 27, y + 32 - h, x + 39, y + 32, 0xFFFF9020);
            }
            case CELL_RACK -> bar(g, x + 70, y + 26, 98, 8, menu.value(2), menu.value(3), 0xFFE04040);
            case AIR_FILTER_UNIT -> bar(g, x + 70, y + 40, 98, 8, menu.value(2), menu.value(3), 0xFF60C060);
            case METAL_RECYCLER -> bar(g, x + 70, y + 39, 38, 6, menu.value(2), menu.value(3), 0xFFE0E0E0);
            case WATER_PURIFIER -> {
                tank(g, x + 60, y + 18, 18, 52, menu.value(2), WaterPurifierBlockEntity.RAW_CAPACITY, 0xFF3060A0);
                tank(g, x + 120, y + 18, 18, 52, menu.value(3), WaterPurifierBlockEntity.CLEAN_CAPACITY, 0xFF50B0FF);
                int total = menu.value(5);
                int p = total <= 0 ? 0 : Math.round(30f * menu.value(4) / total);
                g.fill(x + 83, y + 40, x + 113, y + 44, DARK);
                if (p > 0) g.fill(x + 83, y + 40, x + 83 + p, y + 44, 0xFFFFFFFF);
            }
            default -> {}
        }
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, long value, long max, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, DARK);
        g.fill(x, y, x + w, y + h, 0xFF202020);
        int fill = max <= 0 ? 0 : (int) Math.min(w, Math.round((double) w * value / max));
        if (fill > 0) g.fill(x, y, x + fill, y + h, color);
    }

    private static void tank(GuiGraphics g, int x, int y, int w, int h, int value, int max, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, DARK);
        g.fill(x, y, x + w, y + h, 0xFF202020);
        int fill = max <= 0 ? 0 : Math.min(h, Math.round((float) h * value / max));
        if (fill > 0) g.fill(x, y + h - fill, x + w, y + h, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040, false);

        MachineStatus status = MachineStatus.byId(menu.value(0));
        boolean good = status == MachineStatus.WORKING || status == MachineStatus.IDLE || status == MachineStatus.FULL;
        Component st = Component.translatable("machine.bunker.status." + status.key());
        g.drawString(font, st, imageWidth - 8 - font.width(st), titleLabelY, good ? 0xFF206020 : 0xFFA02020, false);

        int rate = menu.value(1);
        switch (menu.kind) {
            case FURNACE_GENERATOR -> g.drawString(font, Component.translatable("gui.bunker.machine.output", rate), 50, 30, 0xFF404040, false);
            case CELL_RACK -> {
                g.drawString(font, Component.translatable("gui.bunker.machine.stored", menu.value(2), menu.value(3)), 70, 16, 0xFF404040, false);
                g.drawString(font, Component.translatable("gui.bunker.machine.rate", rate), 70, 40, 0xFF404040, false);
            }
            case SUIT_STATION -> {
                g.drawString(font, Component.translatable("gui.bunker.station.parts"), 48, 12, 0xFF404040, false);
                g.drawString(font, Component.translatable("gui.bunker.machine.use", Math.abs(rate)), 48, 30, 0xFF404040, false);
                g.drawString(font, Component.translatable("gui.bunker.station.material"), 82, 66, 0xFF404040, false);
            }
            case METAL_RECYCLER -> {
                g.drawString(font, Component.translatable("gui.bunker.recycler.hint"), 8, 60, 0xFF404040, false);
                g.drawString(font, Component.translatable("gui.bunker.machine.use", Math.abs(rate)), 8, 70, 0xFF404040, false);
            }
            case AIR_FILTER_UNIT -> {
                g.drawString(font, Component.translatable("gui.bunker.machine.filter", menu.value(2), menu.value(3)), 70, 28, 0xFF404040, false);
                g.drawString(font, Component.translatable("gui.bunker.machine.use", Math.abs(rate)), 70, 54, 0xFF404040, false);
            }
            case WATER_PURIFIER -> {
                g.drawString(font, Component.translatable("gui.bunker.purifier.raw"), 50, 72, 0xFF404040, false);
                g.drawString(font, Component.translatable("gui.bunker.purifier.clean"), 116, 72, 0xFF404040, false);
                g.drawString(font, Component.translatable("gui.bunker.machine.cartridge", menu.value(6), menu.value(7)), 8, 12,
                        0xFF404040, false);
            }
            default -> {}
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (menu.kind == MachineKind.WATER_PURIFIER) {
            if (isHovering(60, 18, 18, 52, mouseX, mouseY)) {
                g.renderTooltip(font, Component.translatable("gui.bunker.tank", menu.value(2), WaterPurifierBlockEntity.RAW_CAPACITY)
                        .withStyle(ChatFormatting.GRAY), mouseX, mouseY);
            } else if (isHovering(120, 18, 18, 52, mouseX, mouseY)) {
                g.renderTooltip(font, Component.translatable("gui.bunker.tank", menu.value(3), WaterPurifierBlockEntity.CLEAN_CAPACITY)
                        .withStyle(ChatFormatting.GRAY), mouseX, mouseY);
            }
        }
        renderTooltip(g, mouseX, mouseY);
    }
}
