package com.shardsofvoid.bunker.loot;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.shardsofvoid.bunker.compat.TaczBridge;
import com.shardsofvoid.bunker.registry.ModMisc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Предметы TACZ из всех ганпаков. kind: gun — оружие, тип по весам weights (pistol, smg, rifle, shotgun, dmr, mg,
 * sniper, rpg); ammo — патроны случайного калибра, кроме гранатомётных (min..max штук); launcher_ammo — только
 * гранатомётные (ракеты, гранаты); attachment — случайный обвес.
 */
public final class TaczEntry extends LootPoolSingletonContainer {
    public static final MapCodec<TaczEntry> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(e -> e.kind),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("weights", Map.of()).forGetter(e -> e.weights),
            Codec.INT.optionalFieldOf("min", 1).forGetter(e -> e.min),
            Codec.INT.optionalFieldOf("max", 1).forGetter(e -> e.max))
            .and(singletonFields(i)).apply(i, TaczEntry::new));

    private final String kind;
    private final Map<String, Double> weights;
    private final int min;
    private final int max;

    private TaczEntry(String kind, Map<String, Double> weights, int min, int max, int weight, int quality,
                      List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.kind = kind;
        this.weights = weights;
        this.min = min;
        this.max = max;
    }

    @Override
    public LootPoolEntryType getType() {
        return ModMisc.TACZ_ENTRY.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        RandomSource r = context.getRandom();
        ItemStack stack = switch (kind) {
            case "gun" -> gun(r);
            case "ammo" -> TaczBridge.randomAmmo(r, min + (max > min ? r.nextInt(max - min + 1) : 0), false);
            case "launcher_ammo" -> TaczBridge.randomAmmo(r, min + (max > min ? r.nextInt(max - min + 1) : 0), true);
            case "attachment" -> TaczBridge.randomAttachment(r);
            default -> ItemStack.EMPTY;
        };
        if (!stack.isEmpty()) output.accept(stack);
    }

    /** Тип по весам; если в ганпаках нет оружия выбранного типа — бросок повторяется среди оставшихся. */
    private ItemStack gun(RandomSource r) {
        java.util.Map<String, Double> left = new java.util.LinkedHashMap<>(weights);
        while (!left.isEmpty()) {
            double total = 0;
            for (double w : left.values()) total += Math.max(0, w);
            if (total <= 0) return ItemStack.EMPTY;
            double roll = r.nextDouble() * total;
            String type = null;
            for (Map.Entry<String, Double> e : left.entrySet()) {
                roll -= Math.max(0, e.getValue());
                if (roll <= 0) {
                    type = e.getKey();
                    break;
                }
            }
            if (type == null) type = left.keySet().iterator().next();
            List<ResourceLocation> guns = TaczBridge.gunsOfType(type);
            if (!guns.isEmpty()) return TaczBridge.gun(guns.get(r.nextInt(guns.size())), r);
            left.remove(type);
        }
        return ItemStack.EMPTY;
    }
}
