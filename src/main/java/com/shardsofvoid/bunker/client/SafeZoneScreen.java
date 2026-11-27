package com.shardsofvoid.bunker.client;

import com.shardsofvoid.bunker.network.BunkerActionPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Интерфейс блока безопасной зоны: полуразмер по осям (Shift — шаг 10) и показ границ. */
public class SafeZoneScreen extends Screen {
    private static final int W = 220;
    private static final int H = 130;
    private final BlockPos pos;
    private CompoundTag data;
    private int left;
    private int top;

    public SafeZoneScreen(BlockPos pos, CompoundTag data) {
        super(Component.translatable("block.bunker.safe_zone"));
        this.pos = pos;
        this.data = data;
    }

    public BlockPos pos() {
        return pos;
    }

    public void update(CompoundTag data) {
        this.data = data;
    }

    private void send(String action, int axis, int delta) {
        CompoundTag t = new CompoundTag();
        t.putString("a", action);
        t.putInt("axis", axis);
        t.putInt("delta", delta);
        PacketDistributor.sendToServer(new BunkerActionPayload(pos, t));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        String[] names = {"X", "Y", "Z", "XYZ"};
        for (int axis = 0; axis < 4; axis++) {
            int ax = axis;
            int y = top + 24 + axis * 20;
            addRenderableWidget(Button.builder(Component.literal("−"), b -> send("radius", ax, -(hasShiftDown() ? 10 : 1)))
                    .bounds(left + W - 58, y, 22, 16).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> send("radius", ax, hasShiftDown() ? 10 : 1))
                    .bounds(left + W - 32, y, 22, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.bunker.button.bounds"), b -> {
            ClientState.highlightMuted = false;
            send("show", 0, 0);
        }).bounds(left + 10, top + H - 24, 98, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.bunker.button.hide_bounds"), b -> ClientState.hideHighlight())
                .bounds(left + W - 108, top + H - 24, 98, 18).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + W, top + H, 0xD0101418);
        g.fill(left, top, left + W, top + 1, 0xFF6FD08C);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + 8, top + 8, 0xFFFFFFFF, true);
        int[] r = data.getIntArray("r");
        if (r.length == 3) {
            String[] keys = {"gui.bunker.safe_zone.x", "gui.bunker.safe_zone.y", "gui.bunker.safe_zone.z"};
            for (int i = 0; i < 3; i++) {
                g.drawString(font, Component.translatable(keys[i], r[i], r[i] * 2 + 1), left + 10, top + 28 + i * 20, 0xFFDDDDDD, false);
            }
            g.drawString(font, Component.translatable("gui.bunker.safe_zone.all"), left + 10, top + 88, 0xFFAAAAAA, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
