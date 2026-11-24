package com.shardsofvoid.bunker.client;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Клиентская подсветка (разделы 4.4, 5.4, 6.3): рамки кубов бункера, до 64 полупрозрачных маркеров
 * недостающих блоков оболочки и допустимые центры новых комнат. Рисуется поверх стен, пока не истёк срок.
 * Вне подсветки нагрузки нет.
 */
public final class HighlightRenderer {
    private HighlightRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientState.highlightUntil <= 0) return;
        if (mc.level.getGameTime() > ClientState.highlightUntil) {
            ClientState.highlightUntil = 0;
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = new PoseStack();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float op = (float) (double) com.shardsofvoid.bunker.config.BunkerClientConfig.HIGHLIGHT_OPACITY.get();
        // рамки кубов (тонкие, сквозь стены, приглушённые)
        BufferBuilder lines = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        List<BlockPos> boxes = ClientState.boxes;
        for (int i = 0; i + 1 < boxes.size(); i += 2) {
            BlockPos a = boxes.get(i);
            BlockPos b = boxes.get(i + 1);
            box(lines, m, a.getX() - 0.002f, a.getY() - 0.002f, a.getZ() - 0.002f,
                    b.getX() + 1.002f, b.getY() + 1.002f, b.getZ() + 1.002f, 0.3f, 0.85f, 1.0f, Math.min(1f, op * 1.6f));
        }
        for (BlockPos p : ClientState.allowed) {
            box(lines, m, p.getX() + 0.2f, p.getY() + 0.2f, p.getZ() + 0.2f,
                    p.getX() + 0.8f, p.getY() + 0.8f, p.getZ() + 0.8f, 0.3f, 1.0f, 0.3f, Math.min(1f, op * 1.6f));
        }
        draw(lines);

        // маркеры недостающих блоков: контур + едва заметная заливка; по умолчанию с проверкой глубины,
        // чтобы десятки маркеров за стенами не сливались в сплошное красное пятно
        if (!ClientState.missing.isEmpty() && op > 0) {
            if (!com.shardsofvoid.bunker.config.BunkerClientConfig.HIGHLIGHT_THROUGH_WALLS.get()) RenderSystem.enableDepthTest();
            BufferBuilder outline = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            for (BlockPos p : ClientState.missing) {
                box(outline, m, p.getX() + 0.05f, p.getY() + 0.05f, p.getZ() + 0.05f,
                        p.getX() + 0.95f, p.getY() + 0.95f, p.getZ() + 0.95f, 1.0f, 0.45f, 0.35f, Math.min(1f, op * 1.5f));
            }
            draw(outline);
            BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (BlockPos p : ClientState.missing) {
                cube(quads, m, p.getX() + 0.3f, p.getY() + 0.3f, p.getZ() + 0.3f,
                        p.getX() + 0.7f, p.getY() + 0.7f, p.getZ() + 0.7f, 1.0f, 0.4f, 0.3f, op * 0.3f);
            }
            draw(quads);
            RenderSystem.disableDepthTest();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void draw(BufferBuilder builder) {
        MeshData mesh = builder.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static void line(BufferBuilder b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float bl, float a) {
        b.addVertex(m, x1, y1, z1).setColor(r, g, bl, a);
        b.addVertex(m, x2, y2, z2).setColor(r, g, bl, a);
    }

    private static void box(BufferBuilder b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                            float r, float g, float bl, float a) {
        line(b, m, x0, y0, z0, x1, y0, z0, r, g, bl, a);
        line(b, m, x0, y0, z1, x1, y0, z1, r, g, bl, a);
        line(b, m, x0, y1, z0, x1, y1, z0, r, g, bl, a);
        line(b, m, x0, y1, z1, x1, y1, z1, r, g, bl, a);
        line(b, m, x0, y0, z0, x0, y1, z0, r, g, bl, a);
        line(b, m, x1, y0, z0, x1, y1, z0, r, g, bl, a);
        line(b, m, x0, y0, z1, x0, y1, z1, r, g, bl, a);
        line(b, m, x1, y0, z1, x1, y1, z1, r, g, bl, a);
        line(b, m, x0, y0, z0, x0, y0, z1, r, g, bl, a);
        line(b, m, x1, y0, z0, x1, y0, z1, r, g, bl, a);
        line(b, m, x0, y1, z0, x0, y1, z1, r, g, bl, a);
        line(b, m, x1, y1, z0, x1, y1, z1, r, g, bl, a);
    }

    private static void quad(BufferBuilder b, Matrix4f m, float[] v, float r, float g, float bl, float a) {
        for (int i = 0; i < 12; i += 3) b.addVertex(m, v[i], v[i + 1], v[i + 2]).setColor(r, g, bl, a);
    }

    private static void cube(BufferBuilder b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float r, float g, float bl, float a) {
        quad(b, m, new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, r, g, bl, a);
        quad(b, m, new float[]{x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0}, r, g, bl, a);
        quad(b, m, new float[]{x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, r, g, bl, a);
        quad(b, m, new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, r, g, bl, a);
        quad(b, m, new float[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, r, g, bl, a);
        quad(b, m, new float[]{x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}, r, g, bl, a);
    }
}
