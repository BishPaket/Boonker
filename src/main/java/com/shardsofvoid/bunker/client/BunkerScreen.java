package com.shardsofvoid.bunker.client;

import com.shardsofvoid.bunker.network.BunkerActionPayload;
import com.shardsofvoid.bunker.network.BunkerScreenPayload;
import com.shardsofvoid.bunker.radiation.RadiationSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Интерфейс ядра / блока комнат (раздел 4.5): чек-лист условий, счётчик оболочки и герметичность,
 * уровень и комнаты, кнопки «Показать границы», «Улучшить», «Отвязать». Все данные — с сервера.
 */
public class BunkerScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 252;
    private static final int MAX_ROOM_ROWS = 10;

    private BunkerScreenPayload data;
    private int left;
    private int top;

    public BunkerScreen(BunkerScreenPayload data) {
        super(Component.translatable(data.room() ? "gui.bunker.room.title" : "gui.bunker.core.title"));
        this.data = data;
    }

    public BlockPos pos() {
        return data.pos();
    }

    public void update(BunkerScreenPayload data) {
        this.data = data;
        rebuildWidgets();
    }

    private void send(int action, BlockPos arg) {
        PacketDistributor.sendToServer(new BunkerActionPayload(data.pos(), action, arg));
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        int bottom = top + PANEL_H - 24;

        Button bounds = Button.builder(Component.translatable("gui.bunker.button.bounds"),
                b -> send(BunkerActionPayload.SHOW_BOUNDS, BlockPos.ZERO)).bounds(left + 8, bottom, 92, 20).build();
        bounds.active = data.canEdit();
        addRenderableWidget(bounds);

        Button upgrade = Button.builder(Component.translatable("gui.bunker.button.upgrade"),
                b -> send(BunkerActionPayload.UPGRADE, BlockPos.ZERO)).bounds(left + 104, bottom, 92, 20).build();
        upgrade.active = data.canEdit() && data.canUpgrade() && data.nextLevel() > 0;
        upgrade.setTooltip(Tooltip.create(data.upgradeInfo()));
        addRenderableWidget(upgrade);

        addRenderableWidget(Button.builder(Component.translatable("gui.bunker.button.close"), b -> onClose())
                .bounds(left + 200, bottom, 92, 20).build());

        if (!data.room()) {
            int shown = 0;
            for (BunkerScreenPayload.RoomEntry room : data.rooms()) {
                if (shown >= MAX_ROOM_ROWS) break;
                BlockPos p = room.pos();
                Button unlink = Button.builder(Component.literal("×"), b -> send(BunkerActionPayload.UNLINK_ROOM, p))
                        .bounds(roomX(shown) + 126, roomY(shown) - 1, 12, 10).build();
                unlink.setTooltip(Tooltip.create(Component.translatable("gui.bunker.button.unlink")));
                unlink.active = data.canEdit();
                addRenderableWidget(unlink);
                shown++;
            }
        }
    }

    private int roomX(int index) {
        return left + 8 + (index / 5) * 144;
    }

    private int roomY(int index) {
        return top + 166 + (index % 5) * 11;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + PANEL_W, top + PANEL_H - 26, 0xC0101418);
        g.fill(left, top, left + PANEL_W, top + 1, 0xFF4A7BA0);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int x = left + 8;
        int y = top + 6;
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x, y, 0xFFFFFFFF, true);
        String owner = data.ownerName();
        g.drawString(font, Component.translatable("gui.bunker.owner", owner), left + PANEL_W - 8 - font.width(
                Component.translatable("gui.bunker.owner", owner)), y, 0xFFAAAAAA, true);
        y += 13;

        g.drawString(font, Component.translatable("gui.bunker.state").append(": ").append(stateText()), x, y, 0xFFFFFFFF, true);
        y += 12;

        if (!data.room()) {
            for (BunkerScreenPayload.Condition c : data.conditions()) {
                MutableComponent line = Component.literal(c.ok() ? "✔ " : "✘ ").append(c.label()).append(": ").append(c.value());
                g.drawString(font, line, x, y, c.ok() ? 0xFF7CDB7C : 0xFFFF6B6B, true);
                y += 10;
            }
        } else {
            g.drawString(font, Component.translatable("gui.bunker.room.linked", data.corePos().getX(), data.corePos().getY(),
                    data.corePos().getZ()), x, y, 0xFFCCCCCC, true);
            y += 10;
        }
        y += 3;

        int shellColor = data.shellPresent() >= data.shellExpected() ? 0xFF7CDB7C : 0xFFFFC857;
        g.drawString(font, Component.translatable("gui.bunker.shell", data.shellPresent(), data.shellExpected()), x, y, shellColor, true);
        g.drawString(font, Component.translatable("gui.bunker.sealed").append(": ").append(data.sealInfo()),
                x + 150, y, data.sealed() ? 0xFF7CDB7C : 0xFFFF6B6B, true);
        y += 12;

        g.drawString(font, Component.translatable(data.room() ? "gui.bunker.level.room" : "gui.bunker.level.core",
                RadiationSystem.roman(data.level())), x, y, 0xFF8FD3FF, true);
        g.drawString(font, Component.translatable("gui.bunker.rooms", data.roomCount(), data.roomLimit()), x + 150, y, 0xFF8FD3FF, true);

        // стоимость и результат улучшения
        int cy = top + 124;
        if (data.nextLevel() > 0) {
            g.drawString(font, Component.translatable("gui.bunker.upgrade.to", RadiationSystem.roman(data.nextLevel())),
                    x, cy - 12, 0xFFFFFFFF, false);
            int ix = x;
            for (BunkerScreenPayload.Cost c : data.cost()) {
                g.renderItem(c.stack(), ix, cy);
                String count = c.have() + "/" + c.stack().getCount();
                g.drawString(font, count, ix, cy + 17, c.have() >= c.stack().getCount() ? 0xFF7CDB7C : 0xFFFF6B6B, false);
                ix += Math.max(30, font.width(count) + 6);
            }
            if (data.cost().isEmpty()) g.drawString(font, Component.translatable("gui.bunker.upgrade.free"), x, cy + 4, 0xFFAAAAAA, false);
            g.drawString(font, data.upgradeInfo(), Math.max(ix + 4, x + 150), cy + 4,
                    data.canUpgrade() ? 0xFF8FD3FF : 0xFFFFC857, false);
        } else {
            g.drawString(font, Component.translatable("gui.bunker.upgrade.max"), x, cy, 0xFFAAAAAA, false);
        }

        if (!data.room()) {
            g.drawString(font, Component.translatable("gui.bunker.rooms.list"), x, top + 155, 0xFFAAAAAA, true);
            if (data.rooms().isEmpty()) {
                g.drawString(font, Component.translatable("gui.bunker.rooms.empty"), x + 4, roomY(0), 0xFF777777, false);
            }
            int shown = 0;
            for (BunkerScreenPayload.RoomEntry r : data.rooms()) {
                if (shown >= MAX_ROOM_ROWS) break;
                g.drawString(font, Component.translatable("gui.bunker.rooms.entry", r.pos().getX(), r.pos().getY(), r.pos().getZ(),
                        RadiationSystem.roman(r.level())), roomX(shown), roomY(shown), 0xFFDDDDDD, false);
                shown++;
            }
            if (!data.allowedRooms().isEmpty()) {
                g.drawString(font, Component.translatable("gui.bunker.rooms.allowed", data.allowedRooms().size()),
                        x, top + 222, 0xFF7CDB7C, false);
            }
        }

        // всплывающие подсказки предметов стоимости — поверх всего
        if (data.nextLevel() > 0) {
            int ix = x;
            for (BunkerScreenPayload.Cost c : data.cost()) {
                String count = c.have() + "/" + c.stack().getCount();
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= cy && mouseY < cy + 16) {
                    g.renderTooltip(font, c.stack(), mouseX, mouseY);
                }
                ix += Math.max(30, font.width(count) + 6);
            }
        }
    }

    private Component stateText() {
        return switch (data.state()) {
            case 2 -> Component.translatable("gui.bunker.state.active").withStyle(ChatFormatting.GREEN);
            case 1 -> Component.translatable("gui.bunker.state.building").withStyle(ChatFormatting.YELLOW);
            default -> Component.translatable("gui.bunker.state.not_ready").withStyle(ChatFormatting.RED);
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
