package com.shardsofvoid.bunker.energy;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.registry.ModMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v3 4.1: солнечная панель — до 20 FE/t в полдень при открытом небе. Рассвет и закат — линейно,
 * ночью 0, дождь ×0.5, гроза ×0.2.
 */
public class SolarPanelBlockEntity extends BunkerMachineBlockEntity {
    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.SOLAR_PANEL_BE.get(), pos, state, MachineKind.SOLAR_PANEL);
    }

    /** Доля мощности от времени суток: 0 на рассвете (0) и закате (12000), 1 в полдень (6000), ночью 0. */
    public static double dayFactor(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        if (t >= 12000) return 0.0;
        return Math.max(0.0, 1.0 - Math.abs(t - 6000) / 6000.0);
    }

    public int produce(boolean active) {
        if (!active) return 0;
        Level lvl = getLevel();
        if (lvl == null) return 0;
        if (!lvl.canSeeSky(worldPosition.above())) {
            setStatus(MachineStatus.NO_SKY);
            return 0;
        }
        double f = dayFactor(lvl.getDayTime());
        if (lvl.isThundering()) f *= BunkerConfig.GEN_SOLAR_THUNDER.get();
        else if (lvl.isRaining()) f *= BunkerConfig.GEN_SOLAR_RAIN.get();
        int rate = (int) Math.round(BunkerConfig.GEN_SOLAR_PEAK.get() * f);
        setStatus(rate > 0 ? MachineStatus.WORKING : MachineStatus.NIGHT);
        return rate;
    }

    @Override
    protected void onStatusChanged() {
        MachineBlocks.updateIndicator(this);
    }
}
