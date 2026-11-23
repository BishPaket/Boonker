package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.shardsofvoid.bunker.BunkerMod;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Данные улучшений ядра и комнат (раздел 7): data/&lt;namespace&gt;/bunker_upgrades/core.json и room.json.
 * <pre>
 * { "levels": [
 *     { "level": 2,
 *       "cost": [ {"item": "bunker:bunker_metal", "count": 16}, {"tag": "c:gems/diamond", "count": 4} ],
 *       "effects": { "max_rooms": 6, "dose_decay": 0.0, "mob_effects": [ {"id": "minecraft:regeneration", "amplifier": 0} ] } }
 * ] }
 * </pre>
 * cost уровня N — стоимость перехода на уровень N. Поддерживаемые эффекты: max_rooms (ядро), dose_decay (ед./с
 * снятия дозы у игроков в объёме), mob_effects (эффекты игрокам в объёме), v3: generation (ядро, FE/t),
 * expansion (бюджет расширения), max_batteries (ядро). Принимаются и camelCase-имена. Пустой effects допустим.
 * Файл из пространства имён, отличного от bunker, перекрывает встроенный.
 */
public final class BunkerUpgrades extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final String METAL = BunkerMod.MODID + ":bunker_metal";

    public record Cost(Optional<Item> item, Optional<TagKey<Item>> tag, int count) {
        public boolean test(ItemStack stack) {
            if (item.isPresent()) return stack.is(item.get());
            return tag.isPresent() && stack.is(tag.get());
        }

        public ItemStack displayStack() {
            Item display = item.orElseGet(() -> tag.flatMap(t -> BuiltInRegistries.ITEM.getTag(t)
                    .flatMap(set -> set.stream().findFirst().map(Holder::value))).orElse(Items.BARRIER));
            return new ItemStack(display, Math.max(1, Math.min(99, count)));
        }

        public int countIn(Player player) {
            int total = 0;
            for (ItemStack s : player.getInventory().items) if (!s.isEmpty() && test(s)) total += s.getCount();
            for (ItemStack s : player.getInventory().offhand) if (!s.isEmpty() && test(s)) total += s.getCount();
            return total;
        }
    }

    public record EffectDef(Holder<MobEffect> effect, int amplifier) {
        public MobEffectInstance create(int duration) {
            return new MobEffectInstance(effect, duration, amplifier, true, false, true);
        }
    }

    /**
     * v3: generation — встроенная генерация ядра, FE/t; expansion — бюджет расширения (клеток суммарно по осям);
     * maxBatteries — сколько аккумуляторов работают в бункере.
     */
    public record LevelDef(int level, List<Cost> cost, int maxRooms, double doseDecay, List<EffectDef> effects,
                           int generation, int expansion, int maxBatteries) {}

    private static List<LevelDef> core;
    private static List<LevelDef> room;

    public BunkerUpgrades() {
        super(GSON, "bunker_upgrades");
    }

    public static LevelDef core(int level) {
        if (core == null) core = defaults(true);
        return find(core, level);
    }

    public static LevelDef room(int level) {
        if (room == null) room = defaults(false);
        return find(room, level);
    }

    public static int maxRooms(int coreLevel) {
        return core(coreLevel).maxRooms();
    }

    private static LevelDef find(List<LevelDef> list, int level) {
        for (LevelDef d : list) if (d.level() == level) return d;
        int i = Math.max(0, Math.min(2, level - 1));
        return new LevelDef(level, List.of(), new int[] {3, 6, 10}[i], 0, List.of(),
                new int[] {35, 50, 80}[i], new int[] {0, 2, 4}[i], new int[] {1, 2, 4}[i]);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        core = pick(data, "core", true);
        room = pick(data, "room", false);
    }

    private static List<LevelDef> pick(Map<ResourceLocation, JsonElement> data, String path, boolean isCore) {
        ResourceLocation chosen = null;
        for (ResourceLocation id : data.keySet()) {
            if (!id.getPath().equals(path)) continue;
            if (chosen == null || chosen.getNamespace().equals(BunkerMod.MODID)) chosen = id;
        }
        if (chosen == null) return defaults(isCore);
        try {
            return parse(data.get(chosen).getAsJsonObject(), isCore);
        } catch (RuntimeException e) {
            BunkerMod.LOGGER.error("Ошибка в {}: {} — используются стартовые значения", chosen, e.toString());
            return defaults(isCore);
        }
    }

    private static List<LevelDef> parse(JsonObject root, boolean isCore) {
        List<LevelDef> out = new ArrayList<>();
        for (JsonElement el : GsonHelper.getAsJsonArray(root, "levels")) {
            JsonObject o = el.getAsJsonObject();
            int level = GsonHelper.getAsInt(o, "level");
            List<Cost> cost = new ArrayList<>();
            for (JsonElement c : GsonHelper.getAsJsonArray(o, "cost", new JsonArray())) {
                JsonObject co = c.getAsJsonObject();
                int count = GsonHelper.getAsInt(co, "count", 1);
                if (co.has("item")) {
                    ResourceLocation id = ResourceLocation.parse(GsonHelper.getAsString(co, "item"));
                    Item item = BuiltInRegistries.ITEM.getOptional(id)
                            .orElseThrow(() -> new IllegalArgumentException("неизвестный предмет " + id));
                    cost.add(new Cost(Optional.of(item), Optional.empty(), count));
                } else {
                    ResourceLocation id = ResourceLocation.parse(GsonHelper.getAsString(co, "tag"));
                    cost.add(new Cost(Optional.empty(), Optional.of(TagKey.create(Registries.ITEM, id)), count));
                }
            }
            JsonObject effects = GsonHelper.getAsJsonObject(o, "effects", new JsonObject());
            LevelDef base = find(defaults(isCore), level);
            int maxRooms = intAlias(effects, "max_rooms", "maxRooms", base.maxRooms());
            int generation = intAlias(effects, "generation", "generation", base.generation());
            int expansion = intAlias(effects, "expansion", "expansion", base.expansion());
            int maxBatteries = intAlias(effects, "max_batteries", "maxBatteries", base.maxBatteries());
            double decay = GsonHelper.getAsDouble(effects, "dose_decay", 0.0);
            List<EffectDef> mobEffects = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(effects, "mob_effects", new JsonArray())) {
                JsonObject eo = e.getAsJsonObject();
                ResourceLocation id = ResourceLocation.parse(GsonHelper.getAsString(eo, "id"));
                BuiltInRegistries.MOB_EFFECT.getHolder(id).ifPresentOrElse(
                        h -> mobEffects.add(new EffectDef(h, GsonHelper.getAsInt(eo, "amplifier", 0))),
                        () -> BunkerMod.LOGGER.warn("bunker_upgrades: неизвестный эффект {}", id));
            }
            out.add(new LevelDef(level, List.copyOf(cost), maxRooms, decay, List.copyOf(mobEffects),
                    Math.max(0, generation), Math.max(0, expansion), Math.max(0, maxBatteries)));
        }
        return List.copyOf(out);
    }

    private static int intAlias(JsonObject o, String snake, String camel, int fallback) {
        if (o.has(snake)) return GsonHelper.getAsInt(o, snake);
        if (o.has(camel)) return GsonHelper.getAsInt(o, camel);
        return fallback;
    }

    /** Стартовые значения из ТЗ на случай, если датапак не найден. */
    private static List<LevelDef> defaults(boolean isCore) {
        if (isCore) {
            return List.of(
                    new LevelDef(1, List.of(), 3, 0, List.of(), 35, 0, 1),
                    new LevelDef(2, List.of(item(METAL, 16), item("minecraft:diamond", 4)), 6, 0, List.of(), 50, 2, 2),
                    new LevelDef(3, List.of(item(METAL, 32), item("minecraft:diamond", 8),
                            item("minecraft:netherite_ingot", 2)), 10, 0, List.of(), 80, 4, 4));
        }
        return List.of(
                new LevelDef(1, List.of(), 0, 0, List.of(), 0, 0, 0),
                new LevelDef(2, List.of(item(METAL, 8), item("minecraft:diamond", 2)), 0, 0, List.of(), 0, 2, 0),
                new LevelDef(3, List.of(item(METAL, 16), item("minecraft:diamond", 4)), 0, 0, List.of(), 0, 4, 0));
    }

    private static Cost item(String id, int count) {
        return new Cost(BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)), Optional.empty(), count);
    }
}
