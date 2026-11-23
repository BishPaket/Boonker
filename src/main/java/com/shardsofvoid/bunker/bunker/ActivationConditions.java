package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.List;

import com.shardsofvoid.bunker.bunker.BunkerGeometry.Cube;
import com.shardsofvoid.bunker.config.BunkerConfig;

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
            // Ядро — непрозрачный блок, поэтому смотрим ячейку над ним.
            boolean sky = level.isLoaded(bunker.core.above()) && level.canSeeSky(bunker.core.above());
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

    /** Чужой бункер, с областью которого пересекается куб ядра. */
    private static Bunker overlappingForeign(ServerLevel level, Bunker bunker) {
        Cube core = BunkerGeometry.coreCube(bunker.core);
        for (Bunker other : BunkerManager.get(level).all()) {
            if (other == bunker) continue;
            for (Cube c : BunkerGeometry.cubes(other)) {
                if (c.intersects(core)) return other;
            }
        }
        return null;
    }
}
