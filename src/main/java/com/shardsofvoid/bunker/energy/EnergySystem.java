package com.shardsofvoid.bunker.energy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.shardsofvoid.bunker.bunker.Area;
import com.shardsofvoid.bunker.bunker.Bunker;
import com.shardsofvoid.bunker.bunker.BunkerManager;
import com.shardsofvoid.bunker.bunker.BunkerUpgrades;
import com.shardsofvoid.bunker.config.BunkerConfig;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Энергосистема бункера (ТЗ v3, 3–5). Раз в bunker.energy.interval тиков считает баланс сразу за весь интервал:
 * выработка (ядро, генераторы, внешний ввод) → потребление (области, установки) → заряд/разряд хранилищ →
 * отключение нагрузки по приоритету → восстановление. Потиковой передачи FE между блоками нет.
 */
public final class EnergySystem {
    private EnergySystem() {}

    // ================================================================ кабели (4.3)

    /** Пересчитать граф кабелей всех бункеров измерения. Вызывается только после изменений. */
    public static void rebuildCables(BunkerManager m) {
        m.cablesDirty = false;
        m.cableMembers.clear();
        java.util.Set<UUID> wasTooBig = new java.util.HashSet<>(m.cableTooBig);
        m.cableTooBig.clear();
        ServerLevel level = m.level();
        int max = BunkerConfig.ENERGY_MAX_CABLE_NETWORK.get();
        for (Bunker b : m.all()) {
            List<BunkerMachineBlockEntity> inlets = new ArrayList<>();
            for (BunkerMachineBlockEntity be : m.machines()) {
                if (be.kind == MachineKind.POWER_INLET && !be.isRemoved() && insideAreas(b, be.getBlockPos()) && ownerOk(level, b, be)) {
                    inlets.add(be);
                }
            }
            sortByConnection(b, inlets);
            LongOpenHashSet members = new LongOpenHashSet();
            boolean tooBig = false;
            int limit = Math.min(inlets.size(), BunkerConfig.ENERGY_MAX_INLETS.get());
            for (int i = 0; i < limit && !tooBig; i++) {
                LongOpenHashSet found = new LongOpenHashSet();
                if (!walk(m, inlets.get(i).getBlockPos(), max, found)) tooBig = true;
                else members.addAll(found);
            }
            if (tooBig) {
                m.cableTooBig.add(b.id);
                if (!wasTooBig.contains(b.id)) {
                    m.notifyOwner(b, Component.translatable("message.bunker.cable_too_big", max).withStyle(ChatFormatting.RED), false);
                }
                members.clear();
            }
            m.cableMembers.put(b.id, members);
        }
    }

    /** Обход сети кабелей от энерговвода; false — сеть больше лимита. */
    private static boolean walk(BunkerManager m, BlockPos start, int max, LongOpenHashSet found) {
        ServerLevel level = m.level();
        LongOpenHashSet visited = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        queue.add(start);
        int cables = 0;
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = cur.relative(d);
                long key = n.asLong();
                if (visited.contains(key) || !level.isLoaded(n)) continue;
                visited.add(key);
                BlockState state = level.getBlockState(n);
                if (state.getBlock() instanceof com.shardsofvoid.bunker.block.PowerCableBlock) {
                    if (++cables > max) return false;
                    queue.add(n);
                } else if (m.machineAt(n) != null) {
                    found.add(key);
                }
            }
        }
        return true;
    }

    // ================================================================ принадлежность (3.1)

    private static boolean insideAreas(Bunker b, BlockPos pos) {
        for (Area a : b.areas()) if (a.contains(pos)) return true;
        return false;
    }

    /** Владелец блока совпадает с владельцем бункера или является оператором. */
    private static boolean ownerOk(ServerLevel level, Bunker b, BunkerMachineBlockEntity be) {
        UUID owner = be.owner();
        if (owner == null) return false;
        if (owner.equals(b.owner)) return true;
        return level.getServer().getPlayerList().isOp(new GameProfile(owner, be.ownerName()));
    }

    private static void sortByConnection(Bunker b, List<BunkerMachineBlockEntity> list) {
        list.sort((x, y) -> Long.compare(b.connectedAt.getOrDefault(x.getBlockPos().asLong(), Long.MAX_VALUE),
                b.connectedAt.getOrDefault(y.getBlockPos().asLong(), Long.MAX_VALUE)));
    }

    // ================================================================ потребление (3.3)

    /** P_area = P_base + ⌈(V − 27) × k⌉. */
    public static int areaCost(Area a) {
        double base = BunkerConfig.ENERGY_AREA_BASE.get();
        double k = BunkerConfig.ENERGY_AREA_PER_CELL.get();
        return (int) Math.round(base + Math.ceil(Math.max(0, a.volume() - 27) * k));
    }

    public static int filterUnitCost(Bunker b) {
        int cells = b.zone == null ? 0 : b.zone.size();
        return BunkerConfig.ENERGY_FVU_BASE.get() + (int) Math.ceil(cells / (double) BunkerConfig.ENERGY_FVU_CELLS_PER_FE.get());
    }

    // ================================================================ расчёт

    public static void tick(BunkerManager m, Bunker b, int interval) {
        ServerLevel level = m.level();
        long now = level.getGameTime();
        boolean active = b.isActive();
        boolean lifeSupport = BunkerConfig.LIFE_SUPPORT_ENABLED.get();

        // 1. Состав сети: энергоблоки в областях бункера + подключённые кабелем через энерговвод.
        LongOpenHashSet cable = m.cableMembers.getOrDefault(b.id, new LongOpenHashSet());
        List<BunkerMachineBlockEntity> members = new ArrayList<>();
        LongOpenHashSet memberKeys = new LongOpenHashSet();
        for (BunkerMachineBlockEntity be : m.machines()) {
            if (be.isRemoved()) continue;
            // блок принадлежит только одной сети: занят другим существующим бункером — пропускаем
            if (be.bunkerId != null && !be.bunkerId.equals(b.id) && m.byId(be.bunkerId) != null) continue;
            BlockPos p = be.getBlockPos();
            if (!insideAreas(b, p) && !cable.contains(p.asLong())) {
                if (b.id.equals(be.bunkerId)) unlink(be);
                continue;
            }
            if (!ownerOk(level, b, be)) {
                if (b.id.equals(be.bunkerId)) unlink(be);
                continue;
            }
            members.add(be);
            memberKeys.add(p.asLong());
            be.bunkerId = b.id;
            b.connectedAt.putIfAbsent(p.asLong(), now);
        }
        b.connectedAt.keySet().removeIf(k -> !memberKeys.contains(k) && level.isLoaded(BlockPos.of(k)));
        sortByConnection(b, members);

        Map<MachineKind, List<BunkerMachineBlockEntity>> byKind = new EnumMap<>(MachineKind.class);
        for (BunkerMachineBlockEntity be : members) byKind.computeIfAbsent(be.kind, k -> new ArrayList<>()).add(be);

        BunkerUpgrades.LevelDef coreDef = BunkerUpgrades.core(b.level);
        int perType = BunkerConfig.ENERGY_MAX_PER_TYPE.get();
        List<BunkerMachineBlockEntity> furnaces = limit(byKind, MachineKind.FURNACE_GENERATOR, perType);
        List<BunkerMachineBlockEntity> solars = limit(byKind, MachineKind.SOLAR_PANEL, perType);
        List<BunkerMachineBlockEntity> batteries = limit(byKind, MachineKind.BATTERY, coreDef.maxBatteries());
        List<BunkerMachineBlockEntity> racks = limit(byKind, MachineKind.CELL_RACK, 1);
        List<BunkerMachineBlockEntity> inlets = limit(byKind, MachineKind.POWER_INLET, BunkerConfig.ENERGY_MAX_INLETS.get());
        List<BunkerMachineBlockEntity> stations = limit(byKind, MachineKind.SUIT_STATION, BunkerConfig.STATION_MAX_PER_BUNKER.get());
        List<BunkerMachineBlockEntity> fvus = limit(byKind, MachineKind.AIR_FILTER_UNIT, 1);
        List<BunkerMachineBlockEntity> purifiers = limit(byKind, MachineKind.WATER_PURIFIER, 1);

        EnergyReport report = new EnergyReport();
        report.cableTooBig = m.cableTooBig.contains(b.id);

        // 2. Выработка.
        int production = coreDef.generation();
        report.sources.add(new EnergyReport.Source("core", b.core, production, "working", b.level));
        boolean furnaceIndoor = false;
        for (BunkerMachineBlockEntity be : furnaces) {
            int rate = ((FurnaceGeneratorBlockEntity) be).produce(interval, true);
            be.setLastRate(rate);
            production += rate;
            if (rate > 0 && m.inZone(b, be.getBlockPos())) furnaceIndoor = true;
            report.sources.add(new EnergyReport.Source(be.kind.key(), be.getBlockPos(), rate, be.status().key(), 0));
        }
        for (BunkerMachineBlockEntity be : solars) {
            int rate = ((SolarPanelBlockEntity) be).produce(true);
            be.setLastRate(rate);
            production += rate;
            report.sources.add(new EnergyReport.Source(be.kind.key(), be.getBlockPos(), rate, be.status().key(), 0));
        }
        for (BunkerMachineBlockEntity be : inlets) {
            be.setStatus(MachineStatus.WORKING);
            int foreign = ((PowerInletBlockEntity) be).drainForeign() / Math.max(1, interval);
            be.setLastRate(foreign);
            production += foreign;
            if (BunkerConfig.ENERGY_ACCEPT_FOREIGN.get()) {
                report.sources.add(new EnergyReport.Source("foreign", be.getBlockPos(), foreign, foreign > 0 ? "working" : "idle", 0));
            }
        }
        for (MachineKind k : MachineKind.values()) {
            List<BunkerMachineBlockEntity> all = byKind.get(k);
            if (all == null) continue;
            for (BunkerMachineBlockEntity be : all) {
                if (excess(be, furnaces, solars, batteries, racks, inlets, stations, fvus, purifiers)) {
                    be.setStatus(MachineStatus.EXCESS);
                    be.setLastRate(0);
                    if (k.generator) report.sources.add(new EnergyReport.Source(k.key(), be.getBlockPos(), 0, "excess", 0));
                    else report.storages.add(new EnergyReport.Storage(k.key(), be.getBlockPos(), 0, 0, "excess"));
                }
            }
        }

        // 3. Потребление (только в состоянии «Активен»).
        int consumption = 0;
        int index = 0;
        for (Area a : b.areas()) {
            int cost = areaCost(a);
            boolean on = b.isAreaOn(a);
            String status = !active ? "inactive" : a == b.coreArea ? "on" : a.manualOff ? "manual_off" : a.unpowered ? "unpowered" : "on";
            if (active && on) consumption += cost;
            report.consumers.add(new EnergyReport.Consumer(true, a.name, index, a.anchor, a.sizeText(), active && on ? cost : 0, status));
            index++;
        }

        AirFilterUnitBlockEntity fvu = fvus.isEmpty() ? null : (AirFilterUnitBlockEntity) fvus.get(0);
        int fvuCost = 0;
        if (fvu != null) {
            if (active && lifeSupport) {
                fvuCost = filterUnitCost(b);
                consumption += fvuCost;
            }
            fvu.setLastRate(fvuCost);
        }

        WaterPurifierBlockEntity purifier = purifiers.isEmpty() ? null : (WaterPurifierBlockEntity) purifiers.get(0);
        int purifierCost = BunkerConfig.ENERGY_PURIFIER.get();
        boolean purifierWants = false;
        if (purifier != null) {
            MachineStatus st = installationBlocker(m, b, purifier, active && lifeSupport);
            if (st == null) st = purifier.blocker();
            if (st == null) {
                purifierWants = true;
                st = b.purifierShed ? MachineStatus.SHED : MachineStatus.WORKING;
            }
            purifier.setStatus(st);
            if (purifierWants && !b.purifierShed) consumption += purifierCost;
        }

        SuitStationBlockEntity station = stations.isEmpty() ? null : (SuitStationBlockEntity) stations.get(0);
        int stationCost = 0;
        if (station != null) {
            MachineStatus st = installationBlocker(m, b, station, active);
            int parts = station.damagedParts();
            stationCost = parts * BunkerConfig.ENERGY_STATION_PER_PART.get();
            if (st == null) st = parts == 0 ? MachineStatus.IDLE : b.stationShed ? MachineStatus.SHED : MachineStatus.WORKING;
            station.setStatus(st);
            if (st == MachineStatus.WORKING) consumption += stationCost;
            else if (st != MachineStatus.SHED) stationCost = 0;
        }

        // 4. Баланс за интервал: избыток → аккумуляторы поровну → стойка; нехватка ← аккумуляторы ← стойка.
        CellRackBlockEntity rack = racks.isEmpty() ? null : (CellRackBlockEntity) racks.get(0);
        long net = (long) (production - consumption) * interval;
        if (net >= 0) {
            long left = chargeBatteries(batteries, net, interval);
            if (rack != null && rack.canCharge() && left > 0) left -= rack.charge((int) Math.min(Integer.MAX_VALUE, left), interval);
        } else {
            long need = -net;
            need -= dischargeBatteries(batteries, need, interval);
            if (rack != null && rack.canDischarge() && need > 0) need -= rack.discharge((int) Math.min(Integer.MAX_VALUE, need), interval);
            if (need > 0 && active) {
                // 5. Отключение нагрузки (3.5): станция → водоочистка → комнаты от низшего приоритета.
                int deficit = (int) Math.ceil(need / (double) interval);
                if (deficit > 0 && station != null && station.status() == MachineStatus.WORKING) {
                    b.stationShed = true;
                    station.setStatus(MachineStatus.SHED);
                    consumption -= stationCost;
                    deficit -= stationCost;
                    m.notifyOwner(b, Component.translatable("message.bunker.shed.station").withStyle(ChatFormatting.GOLD), false);
                }
                if (deficit > 0 && purifier != null && purifierWants && !b.purifierShed) {
                    b.purifierShed = true;
                    purifier.setStatus(MachineStatus.SHED);
                    consumption -= purifierCost;
                    deficit -= purifierCost;
                    m.notifyOwner(b, Component.translatable("message.bunker.shed.purifier").withStyle(ChatFormatting.GOLD), false);
                }
                List<Area> rooms = new ArrayList<>(b.rooms.values());
                for (int i = rooms.size() - 1; i >= 0 && deficit > 0; i--) {
                    Area a = rooms.get(i);
                    if (!b.isAreaOn(a)) continue;
                    a.unpowered = true;
                    int cost = areaCost(a);
                    consumption -= cost;
                    deficit -= cost;
                    m.notifyOwner(b, Component.translatable("message.bunker.shed.room", roomName(b, a)).withStyle(ChatFormatting.GOLD), false);
                }
            }
        }

        // 6. Восстановление: раз в restoreDelay — одна комната с высшим приоритетом, если избыток её покрывает.
        if (active && now - b.lastRestore >= BunkerConfig.ENERGY_RESTORE_DELAY.get()) {
            b.lastRestore = now;
            int surplus = production - consumption;
            boolean restored = false;
            for (Area a : b.rooms.values()) {
                if (!a.unpowered || a.manualOff) continue;
                if (surplus >= areaCost(a)) {
                    a.unpowered = false;
                    restored = true;
                }
                break;
            }
            if (!restored && b.purifierShed && surplus >= purifierCost) {
                b.purifierShed = false;
            } else if (!restored && b.stationShed && station != null && surplus >= Math.max(stationCost, 1)) {
                b.stationShed = false;
            }
        }

        // 7. Установки работают за счёт энергии этого интервала.
        if (station != null && station.status() == MachineStatus.WORKING) {
            station.repair(Math.max(1, interval / 20));
            station.setLastRate(stationCost);
        } else if (station != null) {
            station.setLastRate(0);
        }
        if (purifier != null) {
            boolean run = purifier.status() == MachineStatus.WORKING;
            if (run) purifier.process(interval);
            purifier.setLastRate(run ? purifierCost : 0);
        }
        if (station != null) report.consumers.add(new EnergyReport.Consumer(false, MachineKind.SUIT_STATION.key(), 0,
                station.getBlockPos(), "", station.lastRate(), station.status().key()));
        if (purifier != null) report.consumers.add(new EnergyReport.Consumer(false, MachineKind.WATER_PURIFIER.key(), 0,
                purifier.getBlockPos(), "", purifier.lastRate(), purifier.status().key()));
        if (fvu != null) report.consumers.add(new EnergyReport.Consumer(false, MachineKind.AIR_FILTER_UNIT.key(), 0,
                fvu.getBlockPos(), "", fvuCost, active ? "working" : "inactive"));

        // Хранилища для отчёта.
        long stored = 0, capacity = 0;
        for (BunkerMachineBlockEntity be : batteries) {
            BatteryBlockEntity bat = (BatteryBlockEntity) be;
            bat.setStatus(MachineStatus.WORKING);
            stored += bat.energy();
            capacity += BatteryBlockEntity.capacity();
            report.storages.add(new EnergyReport.Storage(be.kind.key(), be.getBlockPos(), bat.energy(), BatteryBlockEntity.capacity(), "working"));
        }
        if (rack != null) {
            rack.setStatus(MachineStatus.WORKING);
            stored += rack.stored();
            capacity += rack.capacity();
            report.storages.add(new EnergyReport.Storage(rack.kind.key(), rack.getBlockPos(), rack.stored(), rack.capacity(),
                    rack.mode().name().toLowerCase()));
        }
        report.production = production;
        report.consumption = consumption;
        report.stored = stored;
        report.capacity = capacity;

        // 8. Жизнеобеспечение (раздел 8).
        LifeSupport.tick(m, b, interval, report, fvu, purifier, furnaceIndoor);
        b.energy = report;
    }

    private static void unlink(BunkerMachineBlockEntity be) {
        be.bunkerId = null;
        be.setLastRate(0);
        be.setStatus(MachineStatus.NO_LINK);
    }

    private static List<BunkerMachineBlockEntity> limit(Map<MachineKind, List<BunkerMachineBlockEntity>> byKind, MachineKind kind, int max) {
        List<BunkerMachineBlockEntity> all = byKind.get(kind);
        if (all == null) return List.of();
        return all.subList(0, Math.min(Math.max(0, max), all.size()));
    }

    @SafeVarargs
    private static boolean excess(BunkerMachineBlockEntity be, List<BunkerMachineBlockEntity>... active) {
        for (List<BunkerMachineBlockEntity> list : active) if (list.contains(be)) return false;
        return true;
    }

    /** Почему установка в зоне не может работать: вне зоны, комната выключена, бункер не активен. */
    private static MachineStatus installationBlocker(BunkerManager m, Bunker b, BunkerMachineBlockEntity be, boolean enabled) {
        if (!enabled || !b.isActive()) return MachineStatus.INACTIVE;
        BlockPos p = be.getBlockPos();
        if (!m.inZone(b, p)) return MachineStatus.OUTSIDE;
        Area a = b.areaAt(p.getX(), p.getY(), p.getZ());
        if (a != null && !b.isAreaOn(a)) return MachineStatus.ROOM_OFF;
        return null;
    }

    /** Поровну по аккумуляторам, каждый не больше batteryRate × интервал и свободного места. */
    private static long chargeBatteries(List<BunkerMachineBlockEntity> batteries, long amount, int interval) {
        long left = amount;
        long rate = (long) BunkerConfig.BATTERY_RATE.get() * interval;
        List<BatteryBlockEntity> open = new ArrayList<>();
        for (BunkerMachineBlockEntity be : batteries) open.add((BatteryBlockEntity) be);
        long[] given = new long[open.size()];
        for (int round = 0; round < 4 && left > 0 && !open.isEmpty(); round++) {
            long share = Math.max(1, left / open.size());
            List<BatteryBlockEntity> still = new ArrayList<>();
            for (int i = 0; i < open.size(); i++) {
                BatteryBlockEntity bat = open.get(i);
                int idx = batteries.indexOf(bat);
                long room = Math.min(BatteryBlockEntity.capacity() - bat.energy(), rate - given[idx]);
                long put = Math.min(Math.min(share, room), left);
                if (put > 0) {
                    bat.setEnergy(bat.energy() + (int) put);
                    given[idx] += put;
                    left -= put;
                }
                if (bat.energy() < BatteryBlockEntity.capacity() && given[idx] < rate) still.add(bat);
            }
            open = still;
        }
        return left;
    }

    private static long dischargeBatteries(List<BunkerMachineBlockEntity> batteries, long amount, int interval) {
        long left = amount;
        long rate = (long) BunkerConfig.BATTERY_RATE.get() * interval;
        List<BatteryBlockEntity> open = new ArrayList<>();
        for (BunkerMachineBlockEntity be : batteries) open.add((BatteryBlockEntity) be);
        long[] taken = new long[open.size()];
        for (int round = 0; round < 4 && left > 0 && !open.isEmpty(); round++) {
            long share = Math.max(1, left / open.size());
            List<BatteryBlockEntity> still = new ArrayList<>();
            for (BatteryBlockEntity bat : open) {
                int idx = batteries.indexOf(bat);
                long avail = Math.min(bat.energy(), rate - taken[idx]);
                long take = Math.min(Math.min(share, avail), left);
                if (take > 0) {
                    bat.setEnergy(bat.energy() - (int) take);
                    taken[idx] += take;
                    left -= take;
                }
                if (bat.energy() > 0 && taken[idx] < rate) still.add(bat);
            }
            open = still;
        }
        return amount - left;
    }

    /** Имя области для сообщений: своё или по умолчанию. */
    public static Component roomName(Bunker b, Area a) {
        if (!a.name.isEmpty()) return Component.literal(a.name);
        if (a == b.coreArea) return Component.translatable("gui.bunker.area.core");
        return Component.translatable("gui.bunker.area.room", b.roomIndex(a.anchor));
    }
}
