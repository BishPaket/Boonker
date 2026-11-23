package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.List;

import com.shardsofvoid.bunker.config.BunkerConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Условия активации ядра (раздел 4.2). Установка не блокируется — интерфейс показывает, чего не хватает. */
public final class ActivationConditions {
    public record Result(Component label, Component value, boolean ok) {}

    private ActivationConditions() {}

    public static List<Result> evaluate(ServerLevel level, Bunker bunker) {
        List<Result> out = new ArrayList<>();

        String dim = level.dimension().location().toString();
        boolean dimOk = BunkerConfig.BUNKER_DIMENSIONS.get().contains(dim);
        out.add(new Result(Component.translatable("gui.bunker.cond.dimension"), Component.literal(dim), dimOk));

        int maxY = BunkerConfig.BUNKER_CORE_MAX_Y.get();
        int y = bunker.core.getY();
        out.add(new Result(Component.translatable("gui.bunker.cond.height"),
                Component.translatable("gui.bunker.cond.height.value", y, maxY), y <= maxY));

        if (BunkerConfig.BUNKER_CORE_REQUIRE_UNDERGROUND.get()) {
            // v3 1.2: проверяется над крышей области ядра, а не над самим ядром (оно может стоять в стене).
            Area a = bunker.coreArea;
            BlockPos roof = new BlockPos((a.minX + a.maxX) / 2, a.maxY + 1, (a.minZ + a.maxZ) / 2);
            boolean sky = level.isLoaded(roof) && level.canSeeSky(roof);
            out.add(new Result(Component.translatable("gui.bunker.cond.underground"),
                    Component.translatable(sky ? "gui.bunker.cond.underground.sky" : "gui.bunker.cond.underground.covered"), !sky));
        }

        Bunker other = overlappingForeign(level, bunker);
        out.add(new Result(Component.translatable("gui.bunker.cond.overlap"),
                other == null ? Component.translatable("gui.bunker.cond.overlap.none")
                        : Component.translatable("gui.bunker.cond.overlap.with", other.ownerName),
                other == null));

        int max = BunkerConfig.BUNKER_MAX_PER_PLAYER.get();
        int rank = BunkerManager.ownershipRank(level.getServer(), bunker);
        out.add(new Result(Component.translatable("gui.bunker.cond.limit"),
                Component.translatable("gui.bunker.cond.limit.value", rank, max), rank <= max));
        return out;
    }

    public static boolean allOk(List<Result> results) {
        for (Result r : results) if (!r.ok()) return false;
        return true;
    }

    /** Чужой бункер, с областью которого пересекается область ядра (v3 1.2). */
    private static Bunker overlappingForeign(ServerLevel level, Bunker bunker) {
        return BunkerManager.get(level).foreignOverlap(bunker, bunker.coreArea);
    }
}
