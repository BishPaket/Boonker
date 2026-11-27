package com.shardsofvoid.bunker;

import java.util.Map;
import java.util.TreeMap;

import com.shardsofvoid.bunker.registry.ModBlocks;
import com.shardsofvoid.bunker.registry.ModMisc;
import com.shardsofvoid.bunker.worldgen.BrokenBunkerFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Самопроверка для CI (переменная окружения BUNKER_SELFTEST=1): ставит несколько сломанных бункеров
 * и много раз бросает их таблицу лута, пишет сводку в лог. В обычной игре не делает ничего.
 */
public final class SelfTest {
    private SelfTest() {}

    public static void onServerStarted(ServerStartedEvent event) {
        if (!"1".equals(System.getenv("BUNKER_SELFTEST"))) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        RandomSource random = RandomSource.create(42);
        int placed = 0;
        for (int i = 0; i < 12; i++) {
            BlockPos origin = new BlockPos(1000 + i * 64, -20, 1000);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.getChunk((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz);
            boolean ok = ModMisc.BROKEN_BUNKER.get().place(NoneFeatureConfiguration.INSTANCE, level,
                    level.getChunkSource().getGenerator(), random, origin);
            int metal = 0, chests = 0, other = 0;
            if (ok) {
                placed++;
                for (BlockPos p : BlockPos.betweenClosed(origin.offset(-20, -2, -20), origin.offset(36, 10, 36))) {
                    BlockState s = level.getBlockState(p);
                    if (s.is(ModBlocks.BROKEN_BUNKER_METAL.get())) metal++;
                    else if (s.is(Blocks.CHEST)) chests++;
                    else if (BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals("simpleradio")) other++;
                }
            }
            BunkerMod.LOGGER.info("BUNKER_SELFTEST feature #{} at {}: placed={} brokenMetal={} chestBlocks={} radio={}",
                    i, origin.toShortString(), ok, metal, chests, other);
        }
        BunkerMod.LOGGER.info("BUNKER_SELFTEST features placed {}/12", placed);

        LootTable table = server.reloadableRegistries().getLootTable(BrokenBunkerFeature.LOOT);
        Map<String, Integer> tally = new TreeMap<>();
        int rolls = 400;
        for (int i = 0; i < rolls; i++) {
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                    .create(LootContextParamSets.CHEST);
            for (ItemStack s : table.getRandomItems(params)) {
                tally.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
        }
        BunkerMod.LOGGER.info("BUNKER_SELFTEST loot over {} chests: {}", rolls, tally);
    }
}
