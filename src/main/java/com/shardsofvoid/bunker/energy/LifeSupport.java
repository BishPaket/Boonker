package com.shardsofvoid.bunker.energy;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.bunker.Bunker;
import com.shardsofvoid.bunker.bunker.BunkerManager;
import com.shardsofvoid.bunker.config.BunkerConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Жизнеобеспечение бункера (ТЗ v3, 8): воздух тратится, только пока внутри есть игроки. Вода жильцами не тратится —
 * жажду ведёт отдельный мод, станция водоочистки лишь наполняет бутылки и вёдра. Эффекты воздуха — в RadiationSystem.
 */
public final class LifeSupport {
    private LifeSupport() {}

    public static void tick(BunkerManager m, Bunker b, int interval, EnergyReport report,
                            @Nullable AirFilterUnitBlockEntity fvu, @Nullable WaterPurifierBlockEntity purifier, boolean furnaceIndoor) {
        boolean enabled = BunkerConfig.LIFE_SUPPORT_ENABLED.get();
        report.lifeSupport = enabled;
        report.air = b.air;
        b.waterStock = purifier == null ? 0 : purifier.clean();
        b.waterCapacity = purifier == null ? 0 : WaterPurifierBlockEntity.CLEAN_CAPACITY;
        report.water = b.waterStock;
        report.waterCapacity = b.waterCapacity;
        report.rawWater = purifier == null ? 0 : purifier.raw();
        if (purifier != null) {
            report.purifierStatus = purifier.status().key();
            var c = purifier.items().getStackInSlot(0);
            report.cartridgeRemaining = com.shardsofvoid.bunker.item.FilterItem.remaining(c);
            report.cartridgeMax = c.getItem() instanceof com.shardsofvoid.bunker.item.FilterItem f ? f.resource() : 0;
        }
        if (!enabled || !b.isActive()) {
            b.playersInZone = 0;
            report.players = 0;
            if (fvu != null) {
                fvu.setStatus(MachineStatus.INACTIVE);
                report.filterUnitStatus = MachineStatus.INACTIVE.key();
                fillFilter(report, fvu);
            }
            return;
        }

        // Игроки в зоне (глаза в ячейке защитного объёма).
        int players = 0;
        for (ServerPlayer p : m.level().players()) {
            if (p.isSpectator()) continue;
            BlockPos eye = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
            if (m.inZone(b, eye)) players++;
        }
        b.playersInZone = players;
        report.players = players;
        double minutes = interval / 1200.0;

        // ФВУ (8.2): работает при питании (в сети и бункер активен), фильтре с ресурсом и открытом воздухозаборе.
        boolean fvuWorking = false;
        if (fvu != null) {
            MachineStatus st;
            if (!fvu.hasFilter()) st = MachineStatus.NO_FILTER;
            else if (!fvu.intakeOpen()) st = MachineStatus.INTAKE_CLOSED;
            else st = MachineStatus.WORKING;
            fvu.setStatus(st);
            fvuWorking = st == MachineStatus.WORKING;
            if (fvuWorking) {
                double wear = players;
                if (furnaceIndoor && BunkerConfig.GEN_FURNACE_INDOOR_PENALTY.get()) wear += 1;
                fvu.wear(wear * minutes);
            }
            report.filterUnitStatus = fvu.status().key();
            fillFilter(report, fvu);
            if (fvu.resourceFraction() < 0.1 && !b.warnedFilter) {
                b.warnedFilter = true;
                m.notifyOwner(b, Component.translatable("message.bunker.life.filter_low").withStyle(ChatFormatting.GOLD), false);
            } else if (fvu.resourceFraction() >= 0.1) {
                b.warnedFilter = false;
            }
        }

        // Воздух (8.3).
        double before = b.air;
        if (fvuWorking) b.air = Math.min(100.0, b.air + BunkerConfig.AIR_RESTORE_PER_MIN.get() * minutes);
        else b.air = Math.max(0.0, b.air - BunkerConfig.AIR_DRAIN_PER_PLAYER_MIN.get() * players * minutes);
        if (before != b.air) m.markDirty();
        report.air = b.air;
        if (b.air < 50 && !b.warnedAir) {
            b.warnedAir = true;
            m.notifyOwner(b, Component.translatable("message.bunker.life.air_low").withStyle(ChatFormatting.GOLD), false);
        } else if (b.air >= 50) {
            b.warnedAir = false;
        }

    }

    private static void fillFilter(EnergyReport report, AirFilterUnitBlockEntity fvu) {
        var s = fvu.filterStack();
        report.filterRemaining = com.shardsofvoid.bunker.item.FilterItem.remaining(s);
        report.filterMax = s.getItem() instanceof com.shardsofvoid.bunker.item.FilterItem f ? f.resource() : 0;
    }
}
