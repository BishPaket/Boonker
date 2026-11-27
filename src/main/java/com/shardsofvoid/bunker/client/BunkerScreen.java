package com.shardsofvoid.bunker.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.network.BunkerActionPayload;
import com.shardsofvoid.bunker.network.BunkerScreenPayload;
import com.shardsofvoid.bunker.radiation.RadiationSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Интерфейс ядра и блока комнаты (v3, раздел 6). Ядро: «Обзор», «Комнаты», «Энергия», «Жизнеобеспечение», «Размер».
 * Блок комнаты: «Обзор» и «Размер»; в состоянии «Размещение» — выбор варианта. Все данные и проверки — на сервере.
 */
public class BunkerScreen extends Screen {
    private static final int W = 330;
    private static final int H = 236;
    private static final int ROW = 11;
    private static final int[] CORE_TABS = {0, 1, 2, 3, 4};
    private static final int[] ROOM_TABS = {0, 4};
    private static final String[] TAB_KEYS = {"overview", "rooms", "energy", "life", "size"};
    private static final String[] DIR_KEYS = {"down", "up", "north", "south", "west", "east"};

    private BlockPos pos;
    private boolean room;
    private int tab;
    private CompoundTag data;
    private int left;
    private int top;
    private int scroll;

    /** Переименование: позиция блока области (null — не открыто) и поле ввода. */
    @Nullable
    private BlockPos renaming;
    private String renameDraft = "";
    @Nullable
    private EditBox renameBox;

    /** Подсказки, привязанные к прямоугольникам текста (координаты экрана). */
    private final List<HoverText> hovers = new ArrayList<>();

    private record HoverText(int x, int y, int w, int h, Component text) {}

    public BunkerScreen(BunkerScreenPayload p) {
        super(Component.translatable(p.room() ? "gui.bunker.room.title" : "gui.bunker.core.title"));
        apply(p);
    }

    public BlockPos pos() {
        return pos;
    }

    private void apply(BunkerScreenPayload p) {
        this.pos = p.pos();
        this.room = p.room();
        if (this.tab != p.tab()) scroll = 0;
        this.tab = p.tab();
        this.data = p.data();
    }

    public void update(BunkerScreenPayload p) {
        if (renameBox != null) renameDraft = renameBox.getValue();
        apply(p);
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- отправка

    private void send(String action) {
        send(action, new CompoundTag());
    }

    private void send(String action, CompoundTag args) {
        args.putString("a", action);
        PacketDistributor.sendToServer(new BunkerActionPayload(pos, args));
    }

    private void sendWithPos(String action, BlockPos target, CompoundTag args) {
        args.put("pos", NbtUtils.writeBlockPos(target));
        send(action, args);
    }

    @Override
    public void onClose() {
        send("close");
        super.onClose();
    }

    // ---------------------------------------------------------------- данные

    private Component json(String key) {
        String s = data.getString(key);
        return parse(s);
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

    private boolean placing() {
        return data.getBoolean("placing");
    }

    // ---------------------------------------------------------------- разметка

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        renameBox = null;
        boundsButton = null;
        hovers.clear();

        if (!placing()) {
            int[] tabs = room ? ROOM_TABS : CORE_TABS;
            int tx = left + 4;
            for (int t : tabs) {
                Component label = Component.translatable("gui.bunker.tab." + TAB_KEYS[t]);
                int w = font.width(label) + 12;
                Button b = Button.builder(label, btn -> {
                    CompoundTag a = new CompoundTag();
                    a.putInt("tab", t);
                    renaming = null;
                    send("tab", a);
                }).bounds(tx, top + 4, w, 16).build();
                b.active = t != tab;
                addRenderableWidget(b);
                tx += w + 2;
            }
        }
        addRenderableWidget(Button.builder(Component.literal("✕"), b -> onClose()).bounds(left + W - 20, top + 4, 16, 16).build());

        if (placing()) {
            initPlacing();
        } else {
            switch (tab) {
                case 1 -> initRooms();
                case 4 -> initSize();
                case 2, 3 -> {}
                default -> initOverview();
            }
        }
        if (renaming != null) initRename();
    }

    private int bodyTop() {
        return top + 26;
    }

    private int bottomRow() {
        return top + H - 24;
    }

    private void initOverview() {
        int y = bottomRow();
        boundsButton = addRenderableWidget(Button.builder(boundsLabel(), b -> toggleBounds())
                .bounds(left + 6, y, 100, 20).build());
        Button upgrade = Button.builder(Component.translatable("gui.bunker.button.upgrade"), b -> send("upgrade"))
                .bounds(left + 110, y, 100, 20).build();
        upgrade.active = data.contains("nextLevel") && data.getBoolean("canUpgrade");
        if (data.contains("upgradeInfo")) upgrade.setTooltip(Tooltip.create(json("upgradeInfo")));
        addRenderableWidget(upgrade);
        if (!room) {
            Component lock = Component.translatable(data.getBoolean("doorsLocked") ? "gui.bunker.button.doors_locked" : "gui.bunker.button.doors_open");
            Button b = Button.builder(lock, btn -> send("lock")).bounds(left + 214, y, 110, 20).build();
            b.setTooltip(Tooltip.create(Component.translatable("gui.bunker.button.doors.tooltip")));
            addRenderableWidget(b);
        } else {
            boolean off = data.getBoolean("manualOff");
            Button toggle = Button.builder(Component.translatable(off ? "gui.bunker.button.enable" : "gui.bunker.button.disable"),
                    btn -> send("toggle", confirmArgs(false))).bounds(left + 214, y, 110, 20).build();
            addRenderableWidget(toggle);
            Button rename = Button.builder(Component.literal("✎"), btn -> {
                renaming = pos;
                renameDraft = data.getString("rawName");
                rebuildWidgets();
            }).bounds(left + W - 40, bodyTop(), 16, 14).build();
            rename.setTooltip(Tooltip.create(Component.translatable("gui.bunker.button.rename")));
            addRenderableWidget(rename);
        }
    }

    @Nullable
    private Button boundsButton;

    private static Component boundsLabel() {
        return Component.translatable(ClientState.highlightVisible() ? "gui.bunker.button.hide_bounds" : "gui.bunker.button.bounds");
    }

    /** Показать границы или скрыть контур (сам он гаснет только по таймеру). */
    private void toggleBounds() {
        if (ClientState.highlightVisible()) {
            ClientState.hideHighlight();
        } else {
            ClientState.highlightMuted = false;
            send("bounds");
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (boundsButton != null) boundsButton.setMessage(boundsLabel());
    }

    private static CompoundTag confirmArgs(boolean confirm) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("confirm", confirm);
        return t;
    }

    private ListTag areas() {
        return data.getList("areas", Tag.TAG_COMPOUND);
    }

    private int visibleRows() {
        return 13;
    }

    private void initRooms() {
        ListTag list = areas();
        int rows = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        int y = bodyTop() + 12;
        int roomCount = 0;
        for (int i = 0; i < list.size(); i++) if (!list.getCompound(i).getBoolean("core") && !"placing".equals(list.getCompound(i).getString("status"))) roomCount++;
        int roomIdx = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            boolean core = c.getBoolean("core");
            boolean pendingRow = "placing".equals(c.getString("status"));
            int ri = core || pendingRow ? -1 : roomIdx++;
            if (i < scroll || i >= scroll + rows) continue;
            BlockPos p = NbtUtils.readBlockPos(c, "pos").orElse(BlockPos.ZERO);
            int bx = left + W - 90;
            int by = y + (i - scroll) * (ROW + 3);
            if (pendingRow) continue;
            Button rename = Button.builder(Component.literal("✎"), b -> {
                renaming = p;
                renameDraft = c.getString("raw");
                rebuildWidgets();
            }).bounds(bx, by - 2, 14, 12).build();
            rename.setTooltip(Tooltip.create(Component.translatable("gui.bunker.button.rename")));
            addRenderableWidget(rename);
            if (core) continue;
            Button up = Button.builder(Component.literal("▲"), b -> {
                CompoundTag a = new CompoundTag();
                a.putBoolean("up", true);
                sendWithPos("move", p, a);
            }).bounds(bx + 16, by - 2, 14, 12).build();
            up.active = ri > 0;
            up.setTooltip(Tooltip.create(Component.translatable("gui.bunker.button.priority_up")));
            addRenderableWidget(up);
            Button down = Button.builder(Component.literal("▼"), b -> {
                CompoundTag a = new CompoundTag();
                a.putBoolean("up", false);
                sendWithPos("move", p, a);
            }).bounds(bx + 32, by - 2, 14, 12).build();
            down.active = ri < roomCount - 1;
            down.setTooltip(Tooltip.create(Component.translatable("gui.bunker.button.priority_down")));
            addRenderableWidget(down);
            boolean off = "manual_off".equals(c.getString("status"));
            Button toggle = Button.builder(Component.literal(off ? "○" : "●"), b -> sendWithPos("toggle", p, confirmArgs(false)))
                    .bounds(bx + 48, by - 2, 14, 12).build();
            toggle.setTooltip(Tooltip.create(Component.translatable(off ? "gui.bunker.button.enable" : "gui.bunker.button.disable")));
            addRenderableWidget(toggle);
            Button unlink = Button.builder(Component.literal("×"), b -> sendWithPos("unlink", p, new CompoundTag()))
                    .bounds(bx + 64, by - 2, 14, 12).build();
            unlink.setTooltip(Tooltip.create(Component.translatable("gui.bunker.button.unlink")));
            addRenderableWidget(unlink);
        }
        if (data.contains("confirm")) {
            CompoundTag c = data.getCompound("confirm");
            BlockPos p = NbtUtils.readBlockPos(c, "pos").orElse(BlockPos.ZERO);
            int y2 = bottomRow();
            addRenderableWidget(Button.builder(Component.translatable("gui.bunker.button.disable").withStyle(ChatFormatting.RED),
                    b -> sendWithPos("toggle", p, confirmArgs(true))).bounds(left + W - 194, y2, 90, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
                CompoundTag a = new CompoundTag();
                a.putInt("tab", 1);
                send("tab", a);
            }).bounds(left + W - 100, y2, 90, 20).build());
        }
    }

    private void initSize() {
        byte[] shared = data.getByteArray("shared");
        int bx = left + 10;
        int by = bodyTop() + 52;
        for (int d = 0; d < 6; d++) {
            int dir = d;
            boolean isShared = dir < shared.length && shared[dir] != 0;
            int x = bx + (d % 3) * 106;
            int y = by + (d / 3) * 24;
            Button minus = Button.builder(Component.literal("−"), b -> wall(dir, -1)).bounds(x + 62, y, 18, 18).build();
            Button plus = Button.builder(Component.literal("+"), b -> wall(dir, 1)).bounds(x + 82, y, 18, 18).build();
            if (isShared) {
                minus.active = false;
                plus.active = false;
                Tooltip t = Tooltip.create(Component.translatable("gui.bunker.size.shared_wall"));
                minus.setTooltip(t);
                plus.setTooltip(t);
            }
            addRenderableWidget(minus);
            addRenderableWidget(plus);
        }
        if (data.getBoolean("isCore")) {
            int sy = by + 52;
            boolean canShift = data.getBoolean("canShift");
            int x = bx;
            for (int d = 0; d < 6; d++) {
                int dir = d;
                Button s = Button.builder(Component.translatable("gui.bunker.size.dir." + DIR_KEYS[d]), b -> {
                    CompoundTag a = new CompoundTag();
                    a.putInt("dir", dir);
                    send("shift", a);
                }).bounds(x, sy, 50, 16).build();
                s.active = canShift;
                if (!canShift) s.setTooltip(Tooltip.create(Component.translatable("message.bunker.size.shift_rooms")));
                addRenderableWidget(s);
                x += 52;
            }
            Button wall = Button.builder(Component.translatable("gui.bunker.size.preset.wall"), b -> preset("wall"))
                    .bounds(bx, sy + 20, 100, 16).build();
            Button center = Button.builder(Component.translatable("gui.bunker.size.preset.center"), b -> preset("center"))
                    .bounds(bx + 104, sy + 20, 100, 16).build();
            wall.active = canShift;
            center.active = canShift;
            addRenderableWidget(wall);
            addRenderableWidget(center);
        }
        int y = bottomRow();
        Button tier = Button.builder(Component.translatable("gui.bunker.button.expand_tier", data.getInt("tier"), data.getInt("tierMax")),
                b -> send("expand_tier")).bounds(left + 6, y, 116, 20).build();
        tier.active = data.getBoolean("canTier");
        tier.setTooltip(Tooltip.create(json("tierInfo")));
        addRenderableWidget(tier);
        Button reset = Button.builder(Component.translatable("gui.bunker.button.reset"), b -> send("reset"))
                .bounds(left + W - 200, y, 94, 20).build();
        reset.active = data.getBoolean("changed");
        addRenderableWidget(reset);
        Button apply = Button.builder(Component.translatable("gui.bunker.button.apply"), b -> send("apply"))
                .bounds(left + W - 102, y, 94, 20).build();
        apply.active = data.getBoolean("changed") && !data.contains("error");
        apply.setTooltip(Tooltip.create(Component.translatable("gui.bunker.size.warn_building")));
        addRenderableWidget(apply);
    }

    private void wall(int dir, int delta) {
        CompoundTag a = new CompoundTag();
        a.putInt("dir", dir);
        a.putInt("delta", delta);
        send("wall", a);
    }

    private void preset(String mode) {
        CompoundTag a = new CompoundTag();
        a.putString("mode", mode);
        send("preset", a);
    }

    private void initPlacing() {
        int n = data.getInt("variants");
        int y = bodyTop() + 60;
        Button prev = Button.builder(Component.literal("◀"), b -> step(-1)).bounds(left + 10, y, 30, 20).build();
        Button next = Button.builder(Component.literal("▶"), b -> step(1)).bounds(left + 150, y, 30, 20).build();
        prev.active = n > 1;
        next.active = n > 1;
        addRenderableWidget(prev);
        addRenderableWidget(next);
        Button confirm = Button.builder(Component.translatable("gui.bunker.button.confirm"), b -> send("confirm"))
                .bounds(left + W - 110, bottomRow(), 100, 20).build();
        confirm.active = n > 0;
        addRenderableWidget(confirm);
    }

    private void step(int s) {
        CompoundTag a = new CompoundTag();
        a.putInt("step", s);
        send("variant", a);
    }

    private void initRename() {
        int y = bottomRow() - 26;
        renameBox = new EditBox(font, left + 8, y, 200, 18, Component.translatable("gui.bunker.button.rename"));
        renameBox.setMaxLength(24);
        renameBox.setValue(renameDraft);
        renameBox.setFilter(s -> !s.contains("§"));
        addRenderableWidget(renameBox);
        setInitialFocus(renameBox);
        BlockPos target = renaming;
        addRenderableWidget(Button.builder(Component.translatable("gui.bunker.button.save"), b -> {
            CompoundTag a = new CompoundTag();
            a.putString("name", renameBox == null ? "" : renameBox.getValue());
            if (target != null) sendWithPos("rename", target, a);
            renaming = null;
            rebuildWidgets();
        }).bounds(left + 212, y, 54, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
            renaming = null;
            rebuildWidgets();
        }).bounds(left + 268, y, 54, 18).build());
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (renameBox != null && renameBox.isFocused()) {
            if (key == 256) { // ESC закрывает только поле
                renaming = null;
                rebuildWidgets();
                return true;
            }
            if (key == 257 || key == 335) {
                CompoundTag a = new CompoundTag();
                a.putString("name", renameBox.getValue());
                if (renaming != null) sendWithPos("rename", renaming, a);
                renaming = null;
                rebuildWidgets();
                return true;
            }
            return renameBox.keyPressed(key, scan, mods) || renameBox.canConsumeInput() || super.keyPressed(key, scan, mods);
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (tab == 1 || tab == 2) {
            int before = scroll;
            scroll = Math.max(0, scroll - (int) Math.signum(dy));
            if (scroll != before) rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
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
        hovers.clear();
        int x = left + 8;
        int y = bodyTop();
        if (placing()) {
            renderPlacing(g, x, y);
        } else {
            switch (tab) {
                case 1 -> renderRooms(g, x, y);
                case 2 -> renderEnergy(g, x, y);
                case 3 -> renderLife(g, x, y);
                case 4 -> renderSize(g, x, y);
                default -> renderOverview(g, x, y, mouseX, mouseY);
            }
        }
        if (data.contains("msg")) {
            Component msg = json("msg");
            int color = data.getBoolean("msgOk") ? 0xFF7CDB7C : 0xFFFF6B6B;
            g.drawString(font, msg, left + 8, bottomRow() - (renaming != null ? 40 : 12), color, true);
        }
        for (HoverText h : hovers) {
            if (mouseX >= h.x && mouseX < h.x + h.w && mouseY >= h.y && mouseY < h.y + h.h) {
                g.renderTooltip(font, h.text, mouseX, mouseY);
            }
        }
    }

    private Component stateText() {
        return switch (data.getInt("state")) {
            case 2 -> Component.translatable("gui.bunker.state.active").withStyle(ChatFormatting.GREEN);
            case 1 -> Component.translatable("gui.bunker.state.building").withStyle(ChatFormatting.YELLOW);
            default -> Component.translatable("gui.bunker.state.not_ready").withStyle(ChatFormatting.RED);
        };
    }

    private void header(GuiGraphics g, int x, int y) {
        Component name = json("name").copy().withStyle(ChatFormatting.BOLD);
        g.drawString(font, name, x, y, 0xFFFFFFFF, true);
        Component owner = Component.translatable("gui.bunker.owner", data.getString("owner"));
        g.drawString(font, owner, left + W - 46 - font.width(owner), y, 0xFFAAAAAA, true);
    }

    private void renderOverview(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        header(g, x, y);
        y += 13;
        g.drawString(font, Component.translatable("gui.bunker.state").append(": ").append(stateText()), x, y, 0xFFFFFFFF, true);
        if (data.getBoolean("doorBreach")) {
            g.drawString(font, Component.translatable("gui.bunker.door_breach"), x + 160, y, 0xFFFF6B6B, true);
        }
        y += 12;
        if (!room) {
            ListTag conditions = data.getList("conditions", Tag.TAG_COMPOUND);
            for (int i = 0; i < conditions.size(); i++) {
                CompoundTag c = conditions.getCompound(i);
                boolean ok = c.getBoolean("ok");
                MutableComponent line = Component.literal(ok ? "✔ " : "✘ ").append(parse(c.getString("label"))).append(": ")
                        .append(parse(c.getString("value")));
                g.drawString(font, line, x, y, ok ? 0xFF7CDB7C : 0xFFFF6B6B, true);
                y += 10;
            }
        } else {
            g.drawString(font, Component.translatable("gui.bunker.room.linked_text", data.getString("corePos")), x, y, 0xFFCCCCCC, true);
            y += 10;
            String status = data.getBoolean("manualOff") ? "manual_off" : data.getBoolean("unpowered") ? "unpowered" : "on";
            g.drawString(font, Component.translatable("gui.bunker.room.info", data.getString("size"), data.getInt("fe"),
                    Component.translatable("gui.bunker.area.status." + status)), x, y, 0xFFCCCCCC, true);
            y += 10;
            if (data.getInt("players") > 0) {
                g.drawString(font, Component.translatable("gui.bunker.room.players", data.getInt("players")), x, y, 0xFFAAAAAA, true);
                y += 10;
            }
        }
        y += 3;
        int present = data.getInt("shellPresent");
        int expected = data.getInt("shellExpected");
        g.drawString(font, Component.translatable("gui.bunker.shell", present, expected), x, y, present >= expected ? 0xFF7CDB7C : 0xFFFFC857, true);
        g.drawString(font, Component.translatable("gui.bunker.sealed").append(": ").append(Component.translatable(data.getString("seal"))),
                x + 160, y, data.getBoolean("sealed") ? 0xFF7CDB7C : 0xFFFF6B6B, true);
        y += 12;
        int level = data.getInt("level");
        g.drawString(font, Component.translatable(room ? "gui.bunker.level.room" : "gui.bunker.level.core", RadiationSystem.roman(level)),
                x, y, 0xFF8FD3FF, true);
        if (!room) {
            g.drawString(font, Component.translatable("gui.bunker.rooms", data.getInt("roomCount"), data.getInt("roomLimit")), x + 160, y,
                    0xFF8FD3FF, true);
        }
        y += 16;

        if (data.contains("nextLevel")) {
            g.drawString(font, Component.translatable("gui.bunker.upgrade.to", RadiationSystem.roman(data.getInt("nextLevel"))), x, y,
                    0xFFFFFFFF, false);
            y += 11;
            ListTag cost = data.getList("cost", Tag.TAG_COMPOUND);
            int ix = x;
            for (int i = 0; i < cost.size(); i++) {
                CompoundTag c = cost.getCompound(i);
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(c.getString("item"))),
                        Math.max(1, Math.min(99, c.getInt("count"))));
                g.renderItem(stack, ix, y);
                String count = c.getInt("have") + "/" + c.getInt("count");
                g.drawString(font, count, ix, y + 17, c.getInt("have") >= c.getInt("count") ? 0xFF7CDB7C : 0xFFFF6B6B, false);
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= y && mouseY < y + 16) {
                    hovers.add(new HoverText(ix, y, 16, 16, stack.getHoverName()));
                }
                ix += Math.max(30, font.width(count) + 6);
            }
            if (cost.isEmpty()) g.drawString(font, Component.translatable("gui.bunker.upgrade.free"), x, y + 4, 0xFFAAAAAA, false);
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(json("upgradeInfo"), left + W - 8 - Math.max(ix + 4, x + 160));
            int ly = y;
            for (net.minecraft.util.FormattedCharSequence l : lines) {
                g.drawString(font, l, Math.max(ix + 4, x + 160), ly, data.getBoolean("canUpgrade") ? 0xFF8FD3FF : 0xFFFFC857, false);
                ly += 10;
            }
        } else {
            g.drawString(font, Component.translatable("gui.bunker.upgrade.max"), x, y, 0xFFAAAAAA, false);
        }
    }

    private void renderRooms(GuiGraphics g, int x, int y) {
        g.drawString(font, Component.translatable("gui.bunker.rooms", data.getInt("roomCount"), data.getInt("roomLimit")), x, y, 0xFF8FD3FF, true);
        g.drawString(font, Component.translatable("gui.bunker.rooms.hint"), x + 110, y, 0xFF777777, false);
        y += 12;
        ListTag list = areas();
        int rows = visibleRows();
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
            CompoundTag c = list.getCompound(i);
            int ry = y + (i - scroll) * (ROW + 3);
            String status = c.getString("status");
            int color = switch (status) {
                case "core", "on" -> 0xFF7CDB7C;
                case "manual_off" -> 0xFFAAAAAA;
                case "placing" -> 0xFFFFC857;
                default -> 0xFFFF6B6B;
            };
            BlockPos p = NbtUtils.readBlockPos(c, "pos").orElse(BlockPos.ZERO);
            Component name = parse(c.getString("name"));
            String nameStr = font.plainSubstrByWidth(name.getString(), 92);
            g.drawString(font, nameStr, x, ry, 0xFFFFFFFF, false);
            hovers.add(new HoverText(x, ry, 92, 9, Component.translatable("gui.bunker.rooms.pos", p.getX(), p.getY(), p.getZ())));
            g.drawString(font, c.getString("size"), x + 96, ry, 0xFFCCCCCC, false);
            g.drawString(font, RadiationSystem.roman(c.getInt("level")), x + 136, ry, 0xFF8FD3FF, false);
            g.drawString(font, c.getInt("fe") + " FE/t", x + 152, ry, 0xFFCCCCCC, false);
            Component st = Component.translatable("gui.bunker.area.status." + status);
            g.drawString(font, font.plainSubstrByWidth(st.getString(), 60), x + 196, ry, color, false);
        }
        if (list.size() > rows) {
            g.drawString(font, (scroll + 1) + "–" + Math.min(list.size(), scroll + rows) + " / " + list.size(), x, bottomRow() + 6,
                    0xFF777777, false);
        }
        if (data.contains("confirm")) {
            CompoundTag c = data.getCompound("confirm");
            Component text = Component.translatable("gui.bunker.confirm.disable", parse(c.getString("name")), c.getInt("players"));
            g.fill(left + 4, bottomRow() - 16, left + W - 4, bottomRow() - 3, 0xC0401010);
            g.drawString(font, text, x, bottomRow() - 13, 0xFFFFC857, true);
        }
    }

    private void renderEnergy(GuiGraphics g, int x, int y) {
        CompoundTag e = data.getCompound("energy");
        int production = e.getInt("production");
        int consumption = e.getInt("consumption");
        int balance = production - consumption;
        MutableComponent head = Component.translatable("gui.bunker.energy.header", production, consumption)
                .append(Component.translatable("gui.bunker.energy.balance", (balance >= 0 ? "+" : "") + balance)
                        .withStyle(balance >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        g.drawString(font, head, x, y, 0xFFFFFFFF, true);
        y += 12;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.bunker.energy.sources").withStyle(ChatFormatting.GRAY));
        ListTag src = e.getList("sources", Tag.TAG_COMPOUND);
        for (int i = 0; i < src.size(); i++) {
            CompoundTag s = src.getCompound(i);
            String kind = s.getString("kind");
            MutableComponent name = Component.translatable("gui.bunker.kind." + kind);
            if (kind.equals("core")) name.append(" " + RadiationSystem.roman(s.getInt("level")));
            lines.add(Component.literal(" ").append(name).append(posText(s)).append(" — " + s.getInt("rate") + " FE/t, ")
                    .append(Component.translatable("machine.bunker.status." + s.getString("status"))));
        }
        lines.add(Component.translatable("gui.bunker.energy.consumers").withStyle(ChatFormatting.GRAY));
        ListTag con = e.getList("consumers", Tag.TAG_COMPOUND);
        for (int i = 0; i < con.size(); i++) {
            CompoundTag c = con.getCompound(i);
            MutableComponent line = Component.literal(" ");
            if (c.getBoolean("area")) {
                line.append(ClientPayloadHandler.areaTitle(c.getInt("index"), c.getString("name"), false).copy().withStyle(ChatFormatting.WHITE))
                        .append(" " + c.getString("size") + " — " + c.getInt("rate") + " FE/t, ")
                        .append(Component.translatable("gui.bunker.area.status." + c.getString("status")));
            } else {
                line.append(Component.translatable("gui.bunker.kind." + c.getString("name"))).append(posText(c))
                        .append(" — " + c.getInt("rate") + " FE/t, ")
                        .append(Component.translatable("machine.bunker.status." + c.getString("status")));
            }
            lines.add(line);
        }
        lines.add(Component.translatable("gui.bunker.energy.storages").withStyle(ChatFormatting.GRAY));
        ListTag sto = e.getList("storages", Tag.TAG_COMPOUND);
        for (int i = 0; i < sto.size(); i++) {
            CompoundTag s = sto.getCompound(i);
            lines.add(Component.literal(" ").append(Component.translatable("gui.bunker.kind." + s.getString("kind"))).append(posText(s))
                    .append(" — " + s.getInt("stored") + " / " + s.getInt("capacity") + " FE, ")
                    .append(Component.translatable("machine.bunker.status." + s.getString("status"))));
        }
        if (sto.isEmpty()) lines.add(Component.translatable("gui.bunker.energy.none").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("gui.bunker.energy.total", e.getLong("stored"), e.getLong("capacity")));
        long forecast = e.getLong("forecast");
        if (forecast < 0) {
            lines.add(Component.translatable("gui.bunker.energy.forecast.drain", minutes(-forecast)).withStyle(ChatFormatting.RED));
        } else if (forecast > 0) {
            lines.add(Component.translatable("gui.bunker.energy.forecast.charge", minutes(forecast)).withStyle(ChatFormatting.GREEN));
        }
        if (e.getBoolean("cableTooBig")) lines.add(Component.translatable("gui.bunker.energy.cable_too_big").withStyle(ChatFormatting.RED));

        drawScrolled(g, lines, x, y, (bottomRow() + 16 - y) / 10);
    }

    private static String minutes(long ticks) {
        long min = Math.max(1, Math.round(ticks / 1200.0));
        if (min >= 120) return (min / 60) + " ч " + (min % 60) + " мин";
        return min + " мин";
    }

    private static Component posText(CompoundTag t) {
        BlockPos p = NbtUtils.readBlockPos(t, "pos").orElse(BlockPos.ZERO);
        return Component.literal(" [" + p.getX() + " " + p.getY() + " " + p.getZ() + "]").withStyle(ChatFormatting.DARK_GRAY);
    }

    private void drawScrolled(GuiGraphics g, List<Component> lines, int x, int y, int rows) {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - rows)));
        for (int i = scroll; i < Math.min(lines.size(), scroll + rows); i++) {
            Component l = lines.get(i);
            String s = font.plainSubstrByWidth(l.getString(), W - 16);
            if (s.length() < l.getString().length()) {
                hovers.add(new HoverText(x, y, W - 16, 9, l));
                g.drawString(font, s, x, y, 0xFFDDDDDD, false);
            } else {
                g.drawString(font, l, x, y, 0xFFDDDDDD, false);
            }
            y += 10;
        }
    }

    private void renderLife(GuiGraphics g, int x, int y) {
        CompoundTag l = data.getCompound("life");
        if (!l.getBoolean("enabled")) {
            g.drawString(font, Component.translatable("gui.bunker.life.disabled"), x, y, 0xFFAAAAAA, true);
            return;
        }
        double air = l.getDouble("air");
        int airColor = air < 25 ? 0xFFFF6B6B : air < 50 ? 0xFFFFC857 : 0xFF7CDB7C;
        g.drawString(font, Component.translatable("gui.bunker.life.air", String.format("%.0f", air)), x, y, airColor, true);
        bar(g, x + 160, y, 150, 8, air, 100, airColor);
        y += 14;
        int water = l.getInt("water");
        int cap = l.getInt("waterCapacity");
        g.drawString(font, Component.translatable("gui.bunker.life.water", water, cap), x, y, 0xFF66AAFF, true);
        bar(g, x + 160, y, 150, 8, water, Math.max(1, cap), 0xFF50B0FF);
        y += 12;
        int players = l.getInt("players");
        if (players > 0) {
            g.drawString(font, Component.translatable("gui.bunker.life.players", players), x, y, 0xFFCCCCCC, false);
        } else {
            g.drawString(font, Component.translatable("gui.bunker.life.nobody"), x, y, 0xFF777777, false);
        }
        y += 16;
        g.drawString(font, Component.translatable("gui.bunker.kind.air_filter_unit").withStyle(ChatFormatting.BOLD), x, y, 0xFFFFFFFF, true);
        y += 11;
        String fvu = l.getString("fvu");
        g.drawString(font, fvu.isEmpty() ? Component.translatable("gui.bunker.life.missing")
                : Component.translatable("machine.bunker.status." + fvu), x + 8, y, "working".equals(fvu) ? 0xFF7CDB7C : 0xFFFF6B6B, false);
        if (l.getInt("filterMax") > 0) {
            g.drawString(font, Component.translatable("gui.bunker.machine.filter", l.getInt("filter"), l.getInt("filterMax")), x + 160, y,
                    0xFFCCCCCC, false);
        }
        y += 16;
        g.drawString(font, Component.translatable("gui.bunker.kind.water_purifier").withStyle(ChatFormatting.BOLD), x, y, 0xFFFFFFFF, true);
        y += 11;
        String pur = l.getString("purifier");
        g.drawString(font, pur.isEmpty() ? Component.translatable("gui.bunker.life.missing")
                : Component.translatable("machine.bunker.status." + pur), x + 8, y, "working".equals(pur) || "idle".equals(pur) || "full".equals(pur)
                ? 0xFF7CDB7C : 0xFFFF6B6B, false);
        if (l.getInt("cartridgeMax") > 0) {
            g.drawString(font, Component.translatable("gui.bunker.machine.cartridge", l.getInt("cartridge"), l.getInt("cartridgeMax")), x + 160, y,
                    0xFFCCCCCC, false);
        }
        y += 11;
        g.drawString(font, Component.translatable("gui.bunker.life.raw", l.getInt("raw")), x + 8, y, 0xFFAAAAAA, false);
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, double value, double max, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF373737);
        g.fill(x, y, x + w, y + h, 0xFF202020);
        int fill = max <= 0 ? 0 : (int) Math.min(w, Math.round(w * value / max));
        if (fill > 0) g.fill(x, y, x + fill, y + h, color);
    }

    private void renderSize(GuiGraphics g, int x, int y) {
        header(g, x, y);
        y += 13;
        g.drawString(font, Component.translatable("gui.bunker.size.current", data.getString("curSize"), data.getInt("curFe")), x, y,
                0xFFCCCCCC, true);
        y += 11;
        boolean changed = data.getBoolean("changed");
        if (changed) {
            g.drawString(font, Component.translatable("gui.bunker.size.new", data.getString("draftSize"), data.getInt("draftFe")), x, y,
                    0xFF8FD3FF, true);
        }
        Component exp = Component.translatable("gui.bunker.size.expansion", data.getInt("used"), data.getInt("budget"));
        g.drawString(font, exp, left + W - 8 - font.width(exp), y - 11, data.getInt("used") > data.getInt("budget") ? 0xFFFF6B6B : 0xFF7CDB7C, true);
        Component lim = Component.translatable("gui.bunker.size.limits", data.getInt("maxH"), data.getInt("maxV"));
        g.drawString(font, lim, left + W - 8 - font.width(lim), y, 0xFF777777, false);

        int bx = left + 10;
        int by = bodyTop() + 52;
        for (int d = 0; d < 6; d++) {
            int wx = bx + (d % 3) * 106;
            int wy = by + (d / 3) * 24 + 5;
            g.drawString(font, Component.translatable("gui.bunker.size.dir." + DIR_KEYS[d]), wx, wy, 0xFFFFFFFF, false);
        }
        if (data.getBoolean("isCore")) {
            g.drawString(font, Component.translatable("gui.bunker.size.shift"), bx, by + 42, 0xFFAAAAAA, false);
        }
        int my = bottomRow() - 26;
        if (data.contains("error")) {
            g.drawString(font, json("error"), x, my, 0xFFFF6B6B, true);
        } else if (changed) {
            for (net.minecraft.util.FormattedCharSequence l : font.split(Component.translatable("gui.bunker.size.warn_building"), W - 16)) {
                g.drawString(font, l, x, my, 0xFFFFC857, false);
                my += 10;
            }
        }
    }

    private void renderPlacing(GuiGraphics g, int x, int y) {
        g.drawString(font, Component.translatable("gui.bunker.placing.title").withStyle(ChatFormatting.BOLD), x, y, 0xFFFFC857, true);
        y += 14;
        for (net.minecraft.util.FormattedCharSequence l : font.split(Component.translatable("gui.bunker.placing.hint"), W - 16)) {
            g.drawString(font, l, x, y, 0xFFCCCCCC, false);
            y += 10;
        }
        int n = data.getInt("variants");
        int vy = bodyTop() + 66;
        if (n == 0) {
            g.drawString(font, Component.translatable("message.bunker.room.no_variant"), left + 50, vy, 0xFFFF6B6B, true);
        } else {
            g.drawString(font, Component.translatable("gui.bunker.placing.variant", data.getInt("variant") + 1, n), left + 50, vy, 0xFFFFFFFF, true);
            g.drawString(font, data.getString("variantSize"), left + 200, vy, 0xFFCCCCCC, false);
        }
        g.drawString(font, Component.translatable("gui.bunker.rooms", data.getInt("roomCount"), data.getInt("roomLimit")), x, vy + 30,
                0xFF8FD3FF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
