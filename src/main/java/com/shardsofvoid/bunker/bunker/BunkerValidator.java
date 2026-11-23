package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.List;

import com.shardsofvoid.bunker.bunker.BunkerGeometry.Cube;
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
 * Проверка герметичности (раздел 5.1). Заливка по 6 направлениям от ячейки ядра через любые блоки,
 * кроме блоков тега bunker:shell_blocks. Дошла до границы объёма — утечка; иначе достигнутые ячейки — защитная зона.
 * Работает только по загруженным чанкам и сама их не грузит.
 */
public final class BunkerValidator {
    public enum Outcome { SEALED, LEAK, TOO_BIG, UNLOADED }

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

    /** Кэш чанков на время одной проверки; null — чанк не загружен. */
    private static final class ChunkCache {
        private final ServerLevel level;
        private final Long2ObjectOpenHashMap<LevelChunk> chunks = new Long2ObjectOpenHashMap<>();
        private final LongOpenHashSet missingChunks = new LongOpenHashSet();

        ChunkCache(ServerLevel level) {
            this.level = level;
        }

        /** Состояние блока или null, если чанк не загружен. */
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
        BunkerConfig.Adjacency mode = BunkerConfig.ROOM_ADJACENCY.get();
        List<Cube> cubes = BunkerGeometry.cubes(bunker);
        ChunkCache cache = new ChunkCache(level);

        // 1. Оболочка: присутствует / ожидается, недостающие позиции, открываемые блоки.
        LongLinkedOpenHashSet expected = BunkerGeometry.expectedShell(cubes, mode);
        int present = 0;
        List<BlockPos> missing = new ArrayList<>();
        Long2BooleanOpenHashMap openables = new Long2BooleanOpenHashMap();
        boolean unloaded = false;
        for (LongIterator it = expected.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            BlockState state = cache.get(x, y, z);
            if (state == null) {
                unloaded = true;
                continue;
            }
            if (state.hasProperty(BlockStateProperties.OPEN) && state.is(ModMisc.SHELL_BLOCKS)) {
                openables.put(key, state.getValue(BlockStateProperties.OPEN).booleanValue());
            }
            if (isShell(state)) {
                present++;
            } else if (missing.size() < HighlightPayload.MAX_MARKERS) {
                missing.add(BlockPos.of(key));
            }
        }
        if (unloaded) {
            return new Result(Outcome.UNLOADED, null, present, expected.size(), missing, openables);
        }

        // 2. Заливка.
        int[] b = BunkerGeometry.bounds(cubes, 1);
        int maxCells = BunkerConfig.BUNKER_VALIDATION_MAX_CELLS.get();
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        long start = bunker.core.asLong();
        visited.add(start);
        queue.enqueue(start);
        Outcome outcome = Outcome.SEALED;
        while (!queue.isEmpty()) {
            long cur = queue.dequeueLong();
            int x = BlockPos.getX(cur), y = BlockPos.getY(cur), z = BlockPos.getZ(cur);
            if (x <= b[0] || y <= b[1] || z <= b[2] || x >= b[3] || y >= b[4] || z >= b[5]) {
                outcome = Outcome.LEAK; // дошли до границы ограничивающего объёма
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
                if (isShell(state)) continue;
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
