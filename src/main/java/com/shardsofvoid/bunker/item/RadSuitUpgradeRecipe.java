package com.shardsofvoid.bunker.item;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.shardsofvoid.bunker.registry.ModComponents;
import com.shardsofvoid.bunker.registry.ModItems;
import com.shardsofvoid.bunker.registry.ModMisc;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Улучшение части костюма на кузнечном столе (раздел 2.3). Рецепт задаётся датапаком:
 * <pre>
 * {
 *   "type": "bunker:radsuit_upgrade",
 *   "template": {"item": "bunker:radsuit_upgrade_template"},
 *   "base": {"tag": "bunker:radsuit"},
 *   "addition": {"item": "minecraft:diamond"},
 *   "addition_count": 2,                       // сколько штук нужно в слоте добавки
 *   "extra_cost": [{"ingredient": {...}, "count": 2}],  // списывается из инвентаря игрока
 *   "from_level": 1, "to_level": 2
 * }
 * </pre>
 * Количество в слоте добавки и extra_cost обрабатывает миксин SmithingMenuMixin.
 */
public class RadSuitUpgradeRecipe implements SmithingRecipe {
    public record ExtraCost(Ingredient ingredient, int count) {
        public static final Codec<ExtraCost> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(ExtraCost::ingredient),
                Codec.intRange(1, 64 * 36).optionalFieldOf("count", 1).forGetter(ExtraCost::count)
        ).apply(i, ExtraCost::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ExtraCost> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, ExtraCost::ingredient,
                ByteBufCodecs.VAR_INT, ExtraCost::count,
                ExtraCost::new);
    }

    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final int additionCount;
    private final List<ExtraCost> extraCost;
    private final int fromLevel;
    private final int toLevel;

    public RadSuitUpgradeRecipe(Ingredient template, Ingredient base, Ingredient addition, int additionCount,
                                List<ExtraCost> extraCost, int fromLevel, int toLevel) {
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.additionCount = additionCount;
        this.extraCost = List.copyOf(extraCost);
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
    }

    public int additionCount() {
        return additionCount;
    }

    public List<ExtraCost> extraCost() {
        return extraCost;
    }

    public int fromLevel() {
        return fromLevel;
    }

    public int toLevel() {
        return toLevel;
    }

    public Ingredient addition() {
        return addition;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        ItemStack baseStack = input.base();
        return template.test(input.template())
                && base.test(baseStack)
                && baseStack.getItem() instanceof RadSuitItem
                && RadSuitItem.level(baseStack) == fromLevel
                && addition.test(input.addition())
                && input.addition().getCount() >= additionCount;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        ItemStack result = input.base().copyWithCount(1);
        result.set(ModComponents.RADSUIT_LEVEL.get(), toLevel);
        return result;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(ModItems.RADSUIT_CHESTPLATE.get());
        stack.set(ModComponents.RADSUIT_LEVEL.get(), toLevel);
        return stack;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return template.test(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return addition.test(stack);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, template, base, addition);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModMisc.RADSUIT_UPGRADE.get();
    }

    /** Хватает ли игроку предметов extra_cost в инвентаре. */
    public boolean hasExtraCost(Player player) {
        if (player.hasInfiniteMaterials()) return true;
        for (ExtraCost cost : extraCost) {
            if (count(player, cost.ingredient()) < cost.count()) return false;
        }
        return true;
    }

    /** Списать extra_cost из инвентаря игрока. */
    public void consumeExtraCost(Player player) {
        if (player.hasInfiniteMaterials()) return;
        for (ExtraCost cost : extraCost) {
            int left = cost.count();
            var items = player.getInventory().items;
            for (int i = 0; i < items.size() && left > 0; i++) {
                ItemStack stack = items.get(i);
                if (!stack.isEmpty() && cost.ingredient().test(stack)) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        player.getInventory().setChanged();
    }

    private static int count(Player player, Ingredient ingredient) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && ingredient.test(stack)) total += stack.getCount();
        }
        return total;
    }

    public static class Serializer implements RecipeSerializer<RadSuitUpgradeRecipe> {
        private static final MapCodec<RadSuitUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC.fieldOf("template").forGetter(r -> r.template),
                Ingredient.CODEC.fieldOf("base").forGetter(r -> r.base),
                Ingredient.CODEC.fieldOf("addition").forGetter(r -> r.addition),
                Codec.intRange(1, 64).optionalFieldOf("addition_count", 1).forGetter(r -> r.additionCount),
                ExtraCost.CODEC.listOf().optionalFieldOf("extra_cost", List.of()).forGetter(r -> r.extraCost),
                Codec.intRange(1, 3).fieldOf("from_level").forGetter(r -> r.fromLevel),
                Codec.intRange(1, 3).fieldOf("to_level").forGetter(r -> r.toLevel)
        ).apply(i, RadSuitUpgradeRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, RadSuitUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.template);
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.base);
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.addition);
                    buf.writeVarInt(r.additionCount);
                    ExtraCost.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.extraCost);
                    buf.writeVarInt(r.fromLevel);
                    buf.writeVarInt(r.toLevel);
                },
                buf -> new RadSuitUpgradeRecipe(
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                        buf.readVarInt(),
                        ExtraCost.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                        buf.readVarInt(),
                        buf.readVarInt()));

        @Override
        public MapCodec<RadSuitUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RadSuitUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
