package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.List;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.network.HighlightPayload;
import com.shardsofvoid.bunker.registry.ModMisc;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Проверка герметичности (v2 5.1 с изменениями v3 1.7). Заливка по 6 направлениям стартует со всех свободных ячеек
 * внутреннего объёма всех областей и идёт через любые блоки, кроме блоков тега bunker:shell_blocks.
 * Дошла до границы ограничивающего объёма — утечка. Работает только по загруженным чанкам.
 */
public final class BunkerValidator {
    public enum Outcome { SEALED, LEAK, TOO_BIG, UNLOADED, NO_VOLUME }

    public record Result(Outcome outcome, LongOpenHashSet zone, int shellPresent, int shellExpected,
                         List<BlockPos> missing, Long2BooleanOpenHashMap openables) {
        public boolean complete() {
            return shellPresent >= shellExpected;
        }
    }

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private BunkerValidator() {}

    /** Блок считается оболочкой: он в теге и не открыт (двери/люки/калитки — только закрытые). */
    public static boolean isShell(BlockState state) {
        if (!state.is(ModMisc.SHELL_BLOCKS)) return false;
        return !(state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN));
    }

    /** Оболочка с учётом шлюзового режима: открытая гермодверь в пределах door.graceSeconds считается закрытой. */
    private static boolean isShell(BlockState state, long key, Bunker bunker, long now) {
        if (isShell(state)) return true;
        if (!state.is(ModMisc.AIRLOCKS) || !state.is(ModMisc.SHELL_BLOCKS)) return false;
        Long since = bunker.doorOpenSince.get(key);
        return since != null && now - since < BunkerConfig.DOOR_GRACE_SECONDS.get() * 20L;
    }

    /** Кэш чанков на время одной проверки; null — чанк не загружен. */
    private static final class ChunkCache {
        private final ServerLevel level;
        private final Long2ObjectOpenHashMap<LevelChunk> chunks = new Long2ObjectOpenHashMap<>();
        private final LongOpenHashSet missingChunks = new LongOpenHashSet();

        ChunkCache(ServerLevel level) {
            this.level = level;
        }

        BlockState get(int x, int y, int z) {
            int cx = SectionPos.blockToSectionCoord(x);
            int cz = SectionPos.blockToSectionCoord(z);
            long key = ChunkPos.asLong(cx, cz);
            LevelChunk chunk = chunks.get(key);
            if (chunk == null) {
                if (missingChunks.contains(key)) return null;
                if (!level.hasChunk(cx, cz)) {
                    missingChunks.add(key);
                    return null;
                }
                chunk = level.getChunk(cx, cz);
                chunks.put(key, chunk);
            }
            return chunk.getBlockState(new BlockPos(x, y, z));
        }
    }

    public static Result validate(ServerLevel level, Bunker bunker) {
        List<Area> areas = bunker.areas();
        ChunkCache cache = new ChunkCache(level);
        long now = level.getGameTime();

        // 1. Оболочка: присутствует / ожидается, недостающие позиции, открываемые блоки.
        LongLinkedOpenHashSet expected = BunkerGeometry.expectedShell(areas);
        int present = 0;
        List<BlockPos> missing = new ArrayList<>();
        Long2BooleanOpenHashMap openables = new Long2BooleanOpenHashMap();
        boolean unloaded = false;
        for (LongIterator it = expected.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            BlockState state = cache.get(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
            if (state == null) {
                unloaded = true;
                continue;
            }
            if (state.hasProperty(BlockStateProperties.OPEN) && state.is(ModMisc.SHELL_BLOCKS)) {
                openables.put(key, state.getValue(BlockStateProperties.OPEN).booleanValue());
            }
            if (isShell(state, key, bunker, now)) {
                present++;
            } else if (missing.size() < HighlightPayload.MAX_MARKERS) {
                missing.add(BlockPos.of(key));
            }
        }
        if (unloaded) {
            return new Result(Outcome.UNLOADED, null, present, expected.size(), missing, openables);
        }

        // 2. Стартовые ячейки — все свободные ячейки внутреннего объёма всех областей (v3 1.7).
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        boolean coreHasVolume = false;
        for (Area a : areas) {
            for (int x = a.minX + 1; x < a.maxX; x++) {
                for (int y = a.minY + 1; y < a.maxY; y++) {
                    for (int z = a.minZ + 1; z < a.maxZ; z++) {
                        long key = BlockPos.asLong(x, y, z);
                        if (visited.contains(key)) continue;
                        BlockState state = cache.get(x, y, z);
                        if (state == null) {
                            return new Result(Outcome.UNLOADED, null, present, expected.size(), missing, openables);
                        }
                        if (isShell(state, key, bunker, now)) continue;
                        if (a == bunker.coreArea) coreHasVolume = true;
                        visited.add(key);
                        queue.enqueue(key);
                    }
                }
            }
        }
        if (!coreHasVolume) {
            return new Result(Outcome.NO_VOLUME, null, present, expected.size(), missing, openables);
        }

        // 3. Заливка.
        int[] b = BunkerGeometry.bounds(areas, 1);
        int maxCells = BunkerConfig.BUNKER_VALIDATION_MAX_CELLS.get();
        Outcome outcome = Outcome.SEALED;
        while (!queue.isEmpty()) {
            long cur = queue.dequeueLong();
            int x = BlockPos.getX(cur), y = BlockPos.getY(cur), z = BlockPos.getZ(cur);
            if (x <= b[0] || y <= b[1] || z <= b[2] || x >= b[3] || y >= b[4] || z >= b[5]) {
                outcome = Outcome.LEAK;
                break;
            }
            for (int[] d : DIRS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                long next = BlockPos.asLong(nx, ny, nz);
                if (visited.contains(next)) continue;
                BlockState state = cache.get(nx, ny, nz);
                if (state == null) {
                    return new Result(Outcome.UNLOADED, null, present, expected.size(), missing, openables);
                }
                if (isShell(state, next, bunker, now)) continue;
                visited.add(next);
                if (visited.size() > maxCells) {
                    return new Result(Outcome.TOO_BIG, null, present, expected.size(), missing, openables);
                }
                queue.enqueue(next);
            }
        }
        return new Result(outcome, outcome == Outcome.SEALED ? visited : null, present, expected.size(), missing, openables);
    }
}
