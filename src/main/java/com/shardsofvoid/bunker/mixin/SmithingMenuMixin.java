package com.shardsofvoid.bunker.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.shardsofvoid.bunker.item.RadSuitUpgradeRecipe;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;

/**
 * Рецепт улучшения костюма требует несколько предметов в слоте добавки (addition_count)
 * и, возможно, предметы из инвентаря (extra_cost). Ванильный кузнечный стол тратит по одному
 * предмету из слота — здесь добираем остальное.
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {
    // слоты: 0 — шаблон, 1 — основа, 2 — добавка, 3 — результат
    private Optional<RecipeHolder<SmithingRecipe>> bunker$currentRecipe(Player player) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        SmithingRecipeInput input = new SmithingRecipeInput(self.getSlot(0).getItem(),
                self.getSlot(1).getItem(), self.getSlot(2).getItem());
        return player.level().getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, player.level());
    }

    @Inject(method = "mayPickup", at = @At("RETURN"), cancellable = true)
    private void bunker$checkExtraCost(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        bunker$currentRecipe(player).ifPresent(holder -> {
            if (holder.value() instanceof RadSuitUpgradeRecipe recipe && !recipe.hasExtraCost(player)) cir.setReturnValue(false);
        });
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void bunker$consumeExtraCost(Player player, ItemStack result, CallbackInfo ci) {
        bunker$currentRecipe(player).ifPresent(holder -> {
            if (holder.value() instanceof RadSuitUpgradeRecipe recipe) {
                AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
                ItemStack addition = self.getSlot(2).getItem();
                int extra = recipe.additionCount() - 1; // одну штуку спишет ванильный код
                if (extra > 0 && !addition.isEmpty()) {
                    addition.shrink(Math.min(extra, addition.getCount()));
                    self.getSlot(2).setChanged();
                }
                recipe.consumeExtraCost(player);
            }
        });
    }
}
