package net.pingpoint.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.pingpoint.manager.Ping;
import net.pingpoint.manager.PingManager;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

@Environment(EnvType.CLIENT)
public class PingRenderer {

    /**
     * 3D in-world billboard rendering executed after entities are drawn.
     */
    public static void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        float tickDelta = (context.tickCounter() != null)
            ? context.tickCounter().getTickDelta(false)
            : client.getRenderTickCounter().getTickDelta(false);

        TextRenderer textRenderer = client.textRenderer;
        List<Ping> pings = PingManager.getInstance().getActivePings();

        for (Ping ping : pings) {
            Vec3d pingPos = ping.getInterpolatedPos(tickDelta, client.world);
            double distSq = camPos.squaredDistanceTo(pingPos);
            if (distSq > 128.0 * 128.0) continue;

            double distance = Math.sqrt(distSq);

            // 1. Render pulsing floor ring if attached to ground/block
            if (ping.getEntityId() == -1) {
                renderFloorRing(matrices, consumers, camPos, ping, tickDelta);
            }

            // 2. Render 3D Billboard facing camera
            matrices.push();

            // Hover float animation: smooth vertical sine bobbing
            float age = ping.getAge() + tickDelta;
            float hover = (float) Math.sin(age * 0.15f) * 0.08f;
            matrices.translate(pingPos.x - camPos.x, pingPos.y - camPos.y + hover, pingPos.z - camPos.z);

            // Rotate to face camera orientation
            matrices.multiply(camera.getRotation());

            // Dynamic scaling: pop-in spring, fade shrink, and distance perspective compensation
            float animScale = ping.getScaleMultiplier(tickDelta);
            float distComp = (float) Math.max(1.0, distance * 0.035);
            distComp = Math.min(distComp, 3.5f); // Prevent billboard from growing excessively large
            float baseScale = 0.026f * animScale * distComp;

            // Flip Y so font coordinate system (+Y down) aligns naturally
            matrices.scale(baseScale, -baseScale, baseScale);

            renderBillboard(matrices, consumers, textRenderer, ping, distance, tickDelta);

            matrices.pop();
        }
    }

    private static void renderBillboard(
        MatrixStack matrices,
        VertexConsumerProvider consumers,
        TextRenderer textRenderer,
        Ping ping,
        double distance,
        float tickDelta
    ) {
        float alpha = ping.getAlpha(tickDelta);
        int alphaInt = (int) (alpha * 255.0f) & 0xFF;

        int color = (alphaInt << 24) | (ping.getType().getColor() & 0x00FFFFFF);
        int white = (alphaInt << 24) | 0x00FFFFFF;
        int lightGray = (alphaInt << 24) | 0x00CCCCCC;
        int bgAlpha = (int) (alpha * 0.65f * 255.0f);
        int bgColor = (bgAlpha << 24);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // 1. Author Name (Top)
        String author = ping.getAuthorName();
        if (author != null && !author.isEmpty()) {
            int authorW = textRenderer.getWidth(author);
            textRenderer.draw(
                Text.literal(author),
                -authorW / 2.0f,
                -22.0f,
                lightGray,
                false,
                matrix,
                consumers,
                TextRenderer.TextLayerType.SEE_THROUGH,
                bgColor,
                0xF000F0
            );
        }

        // 2. Central Pin / Marker Badge (e.g. "[ ⚠ DANGER ]" or "[ ◆ HERE ]" dynamically localized)
        String typeName = Text.translatable(ping.getType().getTranslationKey()).getString().toUpperCase();
        String badge = "[ " + ping.getType().getSymbol() + " " + typeName + " ]";
        int badgeW = textRenderer.getWidth(badge);
        textRenderer.draw(
            Text.literal(badge),
            -badgeW / 2.0f,
            -7.0f,
            color,
            false,
            matrix,
            consumers,
            TextRenderer.TextLayerType.SEE_THROUGH,
            bgColor,
            0xF000F0
        );

        // Also draw normal crisp layer when in direct line of sight
        textRenderer.draw(
            Text.literal(badge),
            -badgeW / 2.0f,
            -7.0f,
            color,
            true,
            matrix,
            consumers,
            TextRenderer.TextLayerType.NORMAL,
            0,
            0xF000F0
        );

        // 3. Distance in meters (e.g. "48m")
        String distStr = (int) Math.round(distance) + "m";
        int distW = textRenderer.getWidth(distStr);
        textRenderer.draw(
            Text.literal(distStr),
            -distW / 2.0f,
            8.0f,
            white,
            false,
            matrix,
            consumers,
            TextRenderer.TextLayerType.SEE_THROUGH,
            bgColor,
            0xF000F0
        );
        textRenderer.draw(
            Text.literal(distStr),
            -distW / 2.0f,
            8.0f,
            white,
            true,
            matrix,
            consumers,
            TextRenderer.TextLayerType.NORMAL,
            0,
            0xF000F0
        );
    }

    /**
     * Renders an animated expanding radar ring and crosshair on the ground surface.
     */
    private static void renderFloorRing(
        MatrixStack matrices,
        VertexConsumerProvider consumers,
        Vec3d camPos,
        Ping ping,
        float tickDelta
    ) {
        float age = (float) ping.getAge() + tickDelta;
        float alpha = ping.getAlpha(tickDelta);
        float ringRadius = 0.55f + 0.22f * (float) Math.sin(age * 0.2f);
        float ringAlpha = alpha * (0.5f + 0.35f * (float) Math.sin(age * 0.25f));

        float r = ping.getType().getR();
        float g = ping.getType().getG();
        float b = ping.getType().getB();

        matrices.push();
        matrices.translate(ping.getX() - camPos.x, ping.getY() - camPos.y + 0.02, ping.getZ() - camPos.z);

        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f posMatrix = entry.getPositionMatrix();

        int segments = 24;
        for (int i = 0; i < segments; i++) {
            double a1 = (i * 2.0 * Math.PI) / segments;
            double a2 = ((i + 1) * 2.0 * Math.PI) / segments;
            float x1 = (float) (Math.cos(a1) * ringRadius);
            float z1 = (float) (Math.sin(a1) * ringRadius);
            float x2 = (float) (Math.cos(a2) * ringRadius);
            float z2 = (float) (Math.sin(a2) * ringRadius);

            buffer.vertex(posMatrix, x1, 0.0f, z1).color(r, g, b, ringAlpha).normal(entry, 0.0f, 1.0f, 0.0f);
            buffer.vertex(posMatrix, x2, 0.0f, z2).color(r, g, b, ringAlpha).normal(entry, 0.0f, 1.0f, 0.0f);
        }

        // Center floor crosshair
        float cs = 0.2f;
        buffer.vertex(posMatrix, -cs, 0.0f, 0.0f).color(r, g, b, ringAlpha).normal(entry, 0.0f, 1.0f, 0.0f);
        buffer.vertex(posMatrix,  cs, 0.0f, 0.0f).color(r, g, b, ringAlpha).normal(entry, 0.0f, 1.0f, 0.0f);
        buffer.vertex(posMatrix, 0.0f, 0.0f, -cs).color(r, g, b, ringAlpha).normal(entry, 0.0f, 1.0f, 0.0f);
        buffer.vertex(posMatrix, 0.0f, 0.0f,  cs).color(r, g, b, ringAlpha).normal(entry, 0.0f, 1.0f, 0.0f);

        matrices.pop();
    }

    /**
     * 2D HUD overlay rendering for off-screen ping pointers.
     */
    public static void renderHud(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.options.hudHidden) return;

        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) return;

        Vec3d camPos = camera.getPos();
        float tickDelta = tickCounter.getTickDelta(false);
        TextRenderer textRenderer = client.textRenderer;

        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();

        double fov = client.options.getFov().getValue();
        double aspect = (double) screenWidth / (double) screenHeight;
        double halfFovY = Math.toRadians(fov / 2.0);
        double tanHalfFovY = Math.tan(halfFovY);
        double tanHalfFovX = tanHalfFovY * aspect;

        List<Ping> pings = PingManager.getInstance().getActivePings();
        for (Ping ping : pings) {
            Vec3d pingPos = ping.getInterpolatedPos(tickDelta, client.world);
            double distSq = camPos.squaredDistanceTo(pingPos);
            if (distSq > 128.0 * 128.0) continue;

            double distance = Math.sqrt(distSq);

            // Transform ping vector into camera local space
            Vec3d delta = pingPos.subtract(camPos);
            Vector3f rel = new Vector3f((float) delta.x, (float) delta.y, (float) delta.z);
            Quaternionf camRot = new Quaternionf(camera.getRotation()).conjugate();
            camRot.transform(rel);

            boolean isOffScreen = false;
            float projX = 0.0f;
            float projY = 0.0f;

            // In camera coordinates, looking straight forward is rel.z < 0
            if (rel.z < -0.1f) {
                projX = (rel.x / -rel.z) / (float) tanHalfFovX;
                projY = (rel.y / -rel.z) / (float) tanHalfFovY;
                float screenX = (screenWidth / 2.0f) + projX * (screenWidth / 2.0f);
                float screenY = (screenHeight / 2.0f) - projY * (screenHeight / 2.0f);

                int margin = 36;
                if (screenX < margin || screenX > screenWidth - margin || screenY < margin || screenY > screenHeight - margin) {
                    isOffScreen = true;
                }
            } else {
                // Point is behind camera
                isOffScreen = true;
            }

            if (isOffScreen) {
                renderOffScreenIndicator(drawContext, textRenderer, screenWidth, screenHeight, rel, ping, distance, tickDelta);
            }
        }
    }

    private static void renderOffScreenIndicator(
        DrawContext drawContext,
        TextRenderer textRenderer,
        int screenWidth,
        int screenHeight,
        Vector3f rel,
        Ping ping,
        double distance,
        float tickDelta
    ) {
        float cx = screenWidth / 2.0f;
        float cy = screenHeight / 2.0f;

        // Calculate 2D direction from screen center to the marker
        float dirX, dirY;
        if (rel.z < -0.1f) {
            dirX = rel.x;
            dirY = -rel.y; // Invert Y for screen coordinates
        } else {
            // Behind player: reverse vector so arrow guides player to turn around
            dirX = -rel.x;
            dirY = rel.y;
            if (Math.abs(dirX) < 1e-4f && Math.abs(dirY) < 1e-4f) {
                dirY = 1.0f;
            }
        }

        double angle = Math.atan2(dirY, dirX);
        float padding = 28.0f;
        float boundW = cx - padding;
        float boundH = cy - padding;

        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double distW = boundW / Math.max(1e-4, Math.abs(cos));
        double distH = boundH / Math.max(1e-4, Math.abs(sin));
        double edgeDist = Math.min(distW, distH);

        float edgeX = (float) (cx + cos * edgeDist);
        float edgeY = (float) (cy + sin * edgeDist);

        float alpha = ping.getAlpha(tickDelta);
        int alphaInt = (int) (alpha * 255.0f) & 0xFF;
        int color = (alphaInt << 24) | (ping.getType().getColor() & 0x00FFFFFF);
        int bgAlpha = (int) (alpha * 0.7f * 255.0f);
        int bgColor = (bgAlpha << 24);

        MatrixStack matrices = drawContext.getMatrices();
        matrices.push();
        matrices.translate(edgeX, edgeY, 0.0f);

        // 1. Draw pointer chevron pointing towards edge direction
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float) angle));
        drawChevron(drawContext, color);
        matrices.pop();

        // 2. Draw distance & icon text pill
        String label = ping.getType().getSymbol() + " " + (int) Math.round(distance) + "m";
        int textW = textRenderer.getWidth(label);

        // Shift label towards screen interior so it never clips off-screen
        float labelOffsetX = (float) (-cos * 16.0f) - (textW / 2.0f);
        float labelOffsetY = (float) (-sin * 16.0f) - 4.0f;

        drawContext.fill(
            (int) labelOffsetX - 3,
            (int) labelOffsetY - 2,
            (int) labelOffsetX + textW + 3,
            (int) labelOffsetY + 10,
            bgColor
        );

        drawContext.drawText(
            textRenderer,
            label,
            (int) labelOffsetX,
            (int) labelOffsetY,
            color,
            true
        );

        matrices.pop();
    }

    /**
     * Draws a tactical directional chevron arrow pointing along the local positive X axis.
     */
    private static void drawChevron(DrawContext drawContext, int color) {
        drawContext.fill(6, -1, 9, 2, color);
        drawContext.fill(3, -3, 6, -1, color);
        drawContext.fill(3, 2, 6, 4, color);
        drawContext.fill(0, -5, 3, -3, color);
        drawContext.fill(0, 4, 3, 6, color);
        drawContext.fill(-3, -7, 0, -5, color);
        drawContext.fill(-3, 6, 0, 8, color);
    }
}
