package com.shardsofvoid.bunker.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.shardsofvoid.bunker.block.MachineBlock;
import com.shardsofvoid.bunker.energy.SuitStationBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** v3 7.1: на стойке станции видны вставленные части костюма — шлем сверху, ботинки снизу. */
public class SuitStationRenderer implements BlockEntityRenderer<SuitStationBlockEntity> {
    private static final float[] HEIGHTS = {1.55f, 1.15f, 0.75f, 0.35f};

    public SuitStationRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SuitStationBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().hasProperty(MachineBlock.FACING) ? be.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        if (facing.getAxis().isVertical()) facing = Direction.NORTH;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = be.displayed(i);
            if (stack.isEmpty()) continue;
            pose.pushPose();
            pose.translate(0.5, HEIGHTS[i] * 0.5 + 0.1, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            pose.scale(0.45f, 0.45f, 0.45f);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong() + i);
            pose.popPose();
        }
    }
}
