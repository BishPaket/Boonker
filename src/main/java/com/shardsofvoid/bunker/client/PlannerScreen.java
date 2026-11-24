package com.shardsofvoid.bunker.client;

import java.util.List;

import com.shardsofvoid.bunker.network.BunkerActionPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Пульт проектирования: слева миникарта бункера (сверху или сбоку), справа черновик новой комнаты —
 * к какой области примыкает, сторона, размер по X/Y/Z, смещение — и кнопка «Построить».
 * Все проверки и стройка — на сервере; экран лишь показывает присланное.
 */
public class PlannerScreen extends Screen {
    private static final int W = 372;
    private static final int H = 262;
    private static final int MAP_W = 200;
    private static final int MAP_H = 184;

    private final BlockPos pos;
    private CompoundTag data;
    private int left;
    private int top;

    // проекция карты, пересчитывается при отрисовке
    private int originA;
    private int originB;
    private int scale = 4;
    private int mapX;
    private int mapY;

    public PlannerScreen(BlockPos pos, CompoundTag data) {
        super(Component.translatable("gui.bunker.planner.title"));
        this.pos = pos;
        this.data = data;
    }

    public BlockPos pos() {
        return pos;
    }

    public void update(CompoundTag data) {
        this.data = data;
        rebuildWidgets();
    }

    private void send(String action, CompoundTag args) {
        args.putString("a", action);
        PacketDistributor.sendToServer(new BunkerActionPayload(pos, args));
    }

    private void send(String action) {
        send(action, new CompoundTag());
    }

    private void sendStep(String action, String key, int value) {
        CompoundTag t = new CompoundTag();
        t.putInt(key, value);
        send(action, t);
    }

    private void sendAxis(String action, int axis, int delta) {
        CompoundTag t = new CompoundTag();
        t.putInt("axis", axis);
        t.putInt("delta", delta);
        send(action, t);
    }

    @Override
    public void onClose() {
        send("close");
        super.onClose();
    }

    private static Component parse(String s) {
        if (s == null || s.isEmpty()) return Component.empty();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Component.literal(s);
        try {
            Component c = Component.Serializer.fromJson(s, mc.level.registryAccess());
            return c == null ? Component.empty() : c;
        } catch (RuntimeException e) {
            return Component.literal(s);
        }
    }

    private boolean sideView() {
        return data.getBoolean("sideView");
    }

    // ---------------------------------------------------------------- разметка

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        mapX = left + 8;
        mapY = top + 26;
        int px = left + 8 + MAP_W + 8;
        int y = top + 26;

        addRenderableWidget(Button.builder(Component.literal("✕"), b -> onClose()).bounds(left + W - 20, top + 4, 16, 16).build());

        addRenderableWidget(Button.builder(Component.literal("◀"), b -> sendStep("target", "step", -1)).bounds(px, y + 10, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> sendStep("target", "step", 1)).bounds(px + 136, y + 10, 14, 14).build());
        y += 30;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> sendStep("side", "step", -1)).bounds(px, y + 10, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> sendStep("side", "step", 1)).bounds(px + 136, y + 10, 14, 14).build());
        y += 30;
        int[] size = data.getIntArray("size");
        for (int axis = 0; axis < 3; axis++) {
            int ax = axis;
            int max = axis == 1 ? data.getInt("maxV") : data.getInt("maxH");
            Button minus = Button.builder(Component.literal("−"), b -> sendAxis("size", ax, -1)).bounds(px + 104, y, 22, 14).build();
            Button plus = Button.builder(Component.literal("+"), b -> sendAxis("size", ax, 1)).bounds(px + 128, y, 22, 14).build();
            if (size.length == 3) {
                minus.active = size[axis] > 3;
                plus.active = size[axis] < max;
            }
            addRenderableWidget(minus);
            addRenderableWidget(plus);
            y += 16;
        }
        y += 4;
        boolean vertical = "up".equals(data.getString("side")) || "down".equals(data.getString("side"));
        Button u1 = Button.builder(Component.literal("◀"), b -> sendAxis("offset", 0, -1)).bounds(px + 58, y, 22, 14).build();
        Button u2 = Button.builder(Component.literal("▶"), b -> sendAxis("offset", 0, 1)).bounds(px + 82, y, 22, 14).build();
        Button v1 = Button.builder(Component.literal(vertical ? "▲" : "▼"), b -> sendAxis("offset", 1, -1)).bounds(px + 106, y, 22, 14).build();
        Button v2 = Button.builder(Component.literal(vertical ? "▼" : "▲"), b -> sendAxis("offset", 1, 1)).bounds(px + 128, y, 22, 14).build();
        Tooltip tu = Tooltip.create(Component.translatable("gui.bunker.planner.offset_u"));
        Tooltip tv = Tooltip.create(Component.translatable(vertical ? "gui.bunker.planner.offset_v_flat" : "gui.bunker.planner.offset_v"));
        u1.setTooltip(tu);
        u2.setTooltip(tu);
        v1.setTooltip(tv);
        v2.setTooltip(tv);
        addRenderableWidget(u1);
        addRenderableWidget(u2);
        addRenderableWidget(v1);
        addRenderableWidget(v2);

        int by = top + H - 24;
        addRenderableWidget(Button.builder(Component.translatable(sideView() ? "gui.bunker.planner.view_top" : "gui.bunker.planner.view_side"),
                b -> send("view")).bounds(left + 8, by, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.bunker.planner.preview"), b -> send("preview"))
                .bounds(left + 110, by, 98, 20).build());
        Button build = Button.builder(Component.translatable("gui.bunker.planner.build").withStyle(ChatFormatting.BOLD), b -> send("build"))
                .bounds(px, by, 150, 20).build();
        build.active = !data.contains("error") && data.contains("draft");
        if (data.contains("error")) build.setTooltip(Tooltip.create(parse(data.getString("error"))));
        addRenderableWidget(build);
    }

    // ---------------------------------------------------------------- миникарта

    /** Координаты по осям карты: сверху — X и Z, сбоку — X и Y (Y вверх). */
    private int[] project(int[] b) {
        return sideView() ? new int[] {b[0], b[1], b[3], b[4]} : new int[] {b[0], b[2], b[3], b[5]};
    }

    private void computeProjection() {
        int minA = Integer.MAX_VALUE, minB = Integer.MAX_VALUE, maxA = Integer.MIN_VALUE, maxB = Integer.MIN_VALUE;
        ListTag areas = data.getList("areas", Tag.TAG_COMPOUND);
        for (int i = 0; i <= areas.size(); i++) {
            int[] b = i < areas.size() ? areas.getCompound(i).getIntArray("b") : data.getIntArray("draft");
            if (b.length != 6) continue;
            int[] p = project(b);
            minA = Math.min(minA, p[0]);
            minB = Math.min(minB, p[1]);
            maxA = Math.max(maxA, p[2]);
            maxB = Math.max(maxB, p[3]);
        }
        if (minA == Integer.MAX_VALUE) {
            minA = minB = 0;
            maxA = maxB = 8;
        }
        minA -= 2;
        minB -= 2;
        maxA += 2;
        maxB += 2;
        int wa = maxA - minA + 1, wb = maxB - minB + 1;
        scale = Math.max(2, Math.min(12, Math.min(MAP_W / wa, MAP_H / wb)));
        originA = minA - (MAP_W / scale - wa) / 2;
        originB = sideView() ? maxB + (MAP_H / scale - wb) / 2 : minB - (MAP_H / scale - wb) / 2;
    }

    private int sx(int a) {
        return mapX + (a - originA) * scale;
    }

    private int sy(int b) {
        return sideView() ? mapY + (originB - b) * scale : mapY + (b - originB) * scale;
    }

    /** Прямоугольник клеток [a0..a1]×[b0..b1] на экране, обрезанный по карте. */
    private void rect(GuiGraphics g, int a0, int b0, int a1, int b1, int color) {
        int x0 = sx(a0), x1 = sx(a1 + 1);
        int y0, y1;
        if (sideView()) {
            y0 = sy(b1);
            y1 = sy(b0 - 1);
        } else {
            y0 = sy(b0);
            y1 = sy(b1 + 1);
        }
        x0 = Math.max(mapX, x0);
        y0 = Math.max(mapY, y0);
        x1 = Math.min(mapX + MAP_W, x1);
        y1 = Math.min(mapY + MAP_H, y1);
        if (x1 > x0 && y1 > y0) g.fill(x0, y0, x1, y1, color);
    }

    private void outline(GuiGraphics g, int a0, int b0, int a1, int b1, int color) {
        rect(g, a0, b0, a1, b0, color);
        rect(g, a0, b1, a1, b1, color);
        rect(g, a0, b0, a0, b1, color);
        rect(g, a1, b0, a1, b1, color);
    }

    private static int statusColor(String status) {
        return switch (status) {
            case "core" -> 0xFF3B6EA5;
            case "on" -> 0xFF3F8F55;
            case "manual_off" -> 0xFF5A5A5A;
            default -> 0xFF8F3F3F;
        };
    }

    private void renderMap(GuiGraphics g, int mouseX, int mouseY) {
        computeProjection();
        g.fill(mapX - 1, mapY - 1, mapX + MAP_W + 1, mapY + MAP_H + 1, 0xFF0B0F13);
        g.fill(mapX, mapY, mapX + MAP_W, mapY + MAP_H, 0xFF151B21);
        // сетка раз в 4 блока
        for (int a = originA; a < originA + MAP_W / scale + 1; a++) {
            if (Math.floorMod(a, 4) == 0) g.fill(sx(a), mapY, sx(a) + 1, mapY + MAP_H, 0xFF1C242C);
        }
        ListTag areas = data.getList("areas", Tag.TAG_COMPOUND);
        int target = data.getInt("target");
        Component hover = null;
        for (int i = 0; i < areas.size(); i++) {
            CompoundTag c = areas.getCompound(i);
            int[] p = project(c.getIntArray("b"));
            if (p.length != 4) continue;
            rect(g, p[0], p[1], p[2], p[3], 0xFF2A3440);
            rect(g, p[0] + 1, p[1] + 1, p[2] - 1, p[3] - 1, statusColor(c.getString("status")));
            if (i == target) outline(g, p[0], p[1], p[2], p[3], 0xFFFFD54A);
            if (inside(mouseX, mouseY, p)) hover = parse(c.getString("name"));
        }
        int[] draft = data.getIntArray("draft");
        if (draft.length == 6) {
            int[] p = project(draft);
            boolean ok = !data.contains("error");
            rect(g, p[0], p[1], p[2], p[3], ok ? 0x66FFA040 : 0x66FF4040);
            outline(g, p[0], p[1], p[2], p[3], ok ? 0xFFFFA040 : 0xFFFF4040);
            if (hover == null && inside(mouseX, mouseY, p)) hover = Component.translatable("gui.bunker.planner.new_room");
        }
        marker(g, data.getIntArray("core"), 0xFFFFFFFF);
        marker(g, data.getIntArray("console"), 0xFF59E0FF);
        marker(g, data.getIntArray("player"), 0xFFFFE066);
        // подписи
        Component dirs = sideView() ? Component.translatable("gui.bunker.planner.axes_side") : Component.translatable("gui.bunker.planner.axes_top");
        g.drawString(font, dirs, mapX + 3, mapY + MAP_H - 10, 0xFF8899AA, false);
        if (hover != null) g.renderTooltip(font, hover, mouseX, mouseY);
    }

    private boolean inside(int mouseX, int mouseY, int[] p) {
        int x0 = sx(p[0]), x1 = sx(p[2] + 1);
        int y0 = sideView() ? sy(p[3]) : sy(p[1]);
        int y1 = sideView() ? sy(p[1] - 1) : sy(p[3] + 1);
        return mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1
                && mouseX >= mapX && mouseX < mapX + MAP_W && mouseY >= mapY && mouseY < mapY + MAP_H;
    }

    private void marker(GuiGraphics g, int[] xyz, int color) {
        if (xyz.length != 3) return;
        int a = xyz[0];
        int b = sideView() ? xyz[1] : xyz[2];
        int x = sx(a) + scale / 2, y = (sideView() ? sy(b) : sy(b)) + scale / 2;
        if (x < mapX || y < mapY || x >= mapX + MAP_W || y >= mapY + MAP_H) return;
        int r = Math.max(1, scale / 3);
        g.fill(x - r, y - r, x + r + 1, y + r + 1, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= mapX && mouseX < mapX + MAP_W && mouseY >= mapY && mouseY < mapY + MAP_H) {
            computeProjection();
            ListTag areas = data.getList("areas", Tag.TAG_COMPOUND);
            for (int i = 0; i < areas.size(); i++) {
                int[] p = project(areas.getCompound(i).getIntArray("b"));
                if (p.length == 4 && inside((int) mouseX, (int) mouseY, p)) {
                    sendStep("select", "index", i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + W, top + H, 0xD0101418);
        g.fill(left, top, left + W, top + 1, 0xFF4A7BA0);
        g.fill(left, top + 22, left + W, top + 23, 0xFF2A3B4A);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + 8, top + 8, 0xFFFFFFFF, true);
        Component rooms = Component.translatable("gui.bunker.rooms", data.getInt("rooms"), data.getInt("roomLimit"));
        g.drawString(font, rooms, left + W - 26 - font.width(rooms), top + 8, 0xFF8FD3FF, true);

        renderMap(g, mouseX, mouseY);

        int px = left + 8 + MAP_W + 8;
        int y = top + 26;
        ListTag areas = data.getList("areas", Tag.TAG_COMPOUND);
        int target = data.getInt("target");
        g.drawString(font, Component.translatable("gui.bunker.planner.attach"), px, y, 0xFFAAAAAA, false);
        if (target < areas.size()) {
            Component name = parse(areas.getCompound(target).getString("name"));
            String s = font.plainSubstrByWidth(name.getString(), 116);
            g.drawCenteredString(font, s, px + 75, y + 13, 0xFFFFD54A);
        }
        y += 30;
        g.drawString(font, Component.translatable("gui.bunker.planner.side"), px, y, 0xFFAAAAAA, false);
        g.drawCenteredString(font, Component.translatable("gui.bunker.size.dir." + data.getString("side")), px + 75, y + 13, 0xFFFFFFFF);
        y += 30;
        int[] size = data.getIntArray("size");
        String[] axes = {"x", "y", "z"};
        for (int i = 0; i < 3 && size.length == 3; i++) {
            g.drawString(font, Component.translatable("gui.bunker.planner.size_" + axes[i], size[i]), px, y + 3, 0xFFFFFFFF, false);
            y += 16;
        }
        y += 4;
        g.drawString(font, Component.translatable("gui.bunker.planner.offset"), px, y + 3, 0xFFAAAAAA, false);
        y += 20;

        // сводка
        if (data.contains("draft")) {
            line(g, Component.translatable("gui.bunker.planner.summary", data.getString("draftSize"), data.getInt("draftFe")), px, y, 0xFFCCCCCC);
            y += 10;
            boolean budgetOk = data.getInt("used") <= data.getInt("budget");
            line(g, Component.translatable("gui.bunker.size.expansion", data.getInt("used"), data.getInt("budget")), px, y,
                    budgetOk ? 0xFF7CDB7C : 0xFFFF6B6B);
            y += 10;
            if (data.contains("metal")) {
                int need = data.getInt("metal"), have = data.getInt("metalHave");
                line(g, Component.translatable("gui.bunker.planner.metal", need, have), px, y, have >= need ? 0xFF7CDB7C : 0xFFFF6B6B);
                y += 10;
                line(g, Component.translatable("gui.bunker.planner.dig", data.getInt("dig")), px, y, 0xFFCCCCCC);
                y += 10;
            }
            int lvl = data.getInt("roomItemLevel");
            line(g, lvl > 0 ? Component.translatable("gui.bunker.planner.room_item", com.shardsofvoid.bunker.radiation.RadiationSystem.roman(lvl))
                    : Component.translatable("gui.bunker.planner.room_item_none"), px, y, lvl > 0 ? 0xFF7CDB7C : 0xFFFF6B6B);
        }

        int my = top + H - 38; // две строки сообщения над кнопками
        if (data.contains("msg")) {
            wrap(g, parse(data.getString("msg")), left + 8, my - 10, W - 16, data.getBoolean("msgOk") ? 0xFF7CDB7C : 0xFFFF6B6B);
        } else if (data.contains("error")) {
            wrap(g, parse(data.getString("error")), left + 8, my - 10, W - 16, 0xFFFFC857);
        }
    }

    private void line(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font, font.plainSubstrByWidth(c.getString(), 152), x, y, color, false);
    }

    private void wrap(GuiGraphics g, Component c, int x, int y, int w, int color) {
        List<FormattedCharSequence> lines = font.split(c, w);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), x, y + i * 10, color, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
