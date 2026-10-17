package net.pingpoint.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.pingpoint.PingPointClient;
import net.pingpoint.network.PingType;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class PingRadialScreen extends Screen {

    public record RadialOption(PingType type, double angleDeg) {}

    // 5 radial sectors arranged clockwise starting from Top (12 o'clock)
    private static final RadialOption[] OPTIONS = new RadialOption[]{
        new RadialOption(PingType.ATTENTION, -90.0), // Top (12 o'clock)
        new RadialOption(PingType.CAUTION,   -18.0), // Top-Right (~2 o'clock)
        new RadialOption(PingType.DANGER,     54.0), // Bottom-Right (~4:30 o'clock)
        new RadialOption(PingType.LOOT,      126.0), // Bottom-Left (~7:30 o'clock)
        new RadialOption(PingType.MINE,      198.0)  // Top-Left (~10 o'clock)
    };

    private static final float INNER_RADIUS = 38.0f;
    private static final float OUTER_RADIUS = 94.0f;
    private static final float HOVER_OUTER_RADIUS = 106.0f;
    private static final float GAP_DEG = 3.0f; // Gap between sectors

    private int hoveredIndex = -1;
    private int prevHoveredIndex = -1;

    public PingRadialScreen() {
        super(Text.translatable("gui.pingpoint.radial_menu"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Do nothing: prevents vanilla from applying dark background and blur shader
    }

    @Override
    public void renderInGameBackground(DrawContext context) {
        // Do nothing: keeps in-game world crisp and clear
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        int cx = this.width / 2;
        int cy = this.height / 2;

        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        // Center deadzone
        if (dist < 22.0) {
            hoveredIndex = -1;
        } else {
            double deg = Math.toDegrees(Math.atan2(dy, dx));
            // Normalize angle so -90° (Top) is centered on sector 0
            double norm = (deg + 90.0 + 360.0 + 36.0) % 360.0;
            int sector = (int) (norm / 72.0);
            if (sector >= 0 && sector < OPTIONS.length) {
                hoveredIndex = sector;
            }
        }

        // Play subtle tick sound when hovering a new sector
        if (hoveredIndex != prevHoveredIndex) {
            if (hoveredIndex != -1 && this.client != null && this.client.player != null) {
                this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 1.4f);
            }
            prevHoveredIndex = hoveredIndex;
        }

        Matrix4f matrix = drawContext.getMatrices().peek().getPositionMatrix();
        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getGui());

        // 1. Soft Circular Backdrop Shadow behind the wheel to make icons pop against any background
        drawFilledCircle(buffer, matrix, cx, cy, HOVER_OUTER_RADIUS + 12.0f, 0.0f, 0.0f, 0.0f, 0.35f, 36);

        // 2. Draw Central Donut Hub
        float centerR = INNER_RADIUS - 5.0f;
        drawFilledCircle(buffer, matrix, cx, cy, centerR, 0.05f, 0.08f, 0.12f, 0.90f, 32);

        // Center Hub Border
        float cbR = 0.3f, cbG = 0.35f, cbB = 0.45f, cbA = 0.5f;
        if (hoveredIndex != -1) {
            PingType ht = OPTIONS[hoveredIndex].type();
            cbR = ht.getR();
            cbG = ht.getG();
            cbB = ht.getB();
            cbA = 0.95f;
        }
        drawArcSegment(buffer, matrix, cx, cy, centerR - 2.5f, centerR, 0.0, 360.0, cbR, cbG, cbB, cbA, 32);

        // 3. Draw 5 Circular Pie / Donut Sectors
        for (int i = 0; i < OPTIONS.length; i++) {
            RadialOption opt = OPTIONS[i];
            boolean isHovered = (i == hoveredIndex);
            PingType type = opt.type();

            double centerDeg = opt.angleDeg();
            double halfSpan = (72.0 - GAP_DEG) / 2.0;
            double startDeg = centerDeg - halfSpan;
            double endDeg = centerDeg + halfSpan;

            float curROut = isHovered ? HOVER_OUTER_RADIUS : OUTER_RADIUS;

            // Sector Wedge Background
            if (isHovered) {
                drawArcSegment(buffer, matrix, cx, cy, INNER_RADIUS, curROut, startDeg, endDeg,
                    type.getR(), type.getG(), type.getB(), 0.55f, 16);
            } else {
                drawArcSegment(buffer, matrix, cx, cy, INNER_RADIUS, curROut, startDeg, endDeg,
                    0.06f, 0.09f, 0.14f, 0.72f, 16);
            }

            // Outer Arc Rim (accent glow)
            float rimAlpha = isHovered ? 1.0f : 0.55f;
            float rimThick = isHovered ? 4.0f : 2.0f;
            drawArcSegment(buffer, matrix, cx, cy, curROut - rimThick, curROut, startDeg, endDeg,
                type.getR(), type.getG(), type.getB(), rimAlpha, 16);

            // Inner Arc Rim
            drawArcSegment(buffer, matrix, cx, cy, INNER_RADIUS, INNER_RADIUS + 2.0f, startDeg, endDeg,
                isHovered ? type.getR() : 0.25f,
                isHovered ? type.getG() : 0.30f,
                isHovered ? type.getB() : 0.40f,
                isHovered ? 0.90f : 0.45f,
                16
            );

            // Side Radial Dividers
            float divAlpha = isHovered ? 0.95f : 0.35f;
            drawRadialLine(buffer, matrix, cx, cy, INNER_RADIUS, curROut, startDeg, 1.5f,
                isHovered ? type.getR() : 0.3f,
                isHovered ? type.getG() : 0.35f,
                isHovered ? type.getB() : 0.45f,
                divAlpha
            );
            drawRadialLine(buffer, matrix, cx, cy, INNER_RADIUS, curROut, endDeg, 1.5f,
                isHovered ? type.getR() : 0.3f,
                isHovered ? type.getG() : 0.35f,
                isHovered ? type.getB() : 0.45f,
                divAlpha
            );
        }

        // Flush all circular geometry batch before drawing text
        consumers.draw();

        // 4. Draw Text and Icons on each Sector
        for (int i = 0; i < OPTIONS.length; i++) {
            RadialOption opt = OPTIONS[i];
            boolean isHovered = (i == hoveredIndex);
            PingType type = opt.type();

            double rad = Math.toRadians(opt.angleDeg());
            float curROut = isHovered ? HOVER_OUTER_RADIUS : OUTER_RADIUS;
            float rMid = (INNER_RADIUS + curROut) / 2.0f;

            int tx = (int) (cx + Math.cos(rad) * rMid);
            int ty = (int) (cy + Math.sin(rad) * rMid);

            String localizedTitle = Text.translatable(type.getTranslationKey()).getString();
            String sym = type.getSymbol();

            int symW = this.textRenderer.getWidth(sym);
            int titleW = this.textRenderer.getWidth(localizedTitle);

            int symColor = isHovered ? 0xFFFFFFFF : (0xEE000000 | type.getColor());
            int textColor = isHovered ? 0xFFFFFFFF : 0xCCD0D5DD;

            // Draw symbol
            drawContext.drawText(this.textRenderer, sym, tx - symW / 2, ty - 8, symColor, true);
            // Draw title
            drawContext.drawText(this.textRenderer, localizedTitle, tx - titleW / 2, ty + 2, textColor, true);
        }

        // 5. Draw Center Content
        if (hoveredIndex != -1) {
            PingType ht = OPTIONS[hoveredIndex].type();
            String sym = ht.getSymbol();
            String title = Text.translatable(ht.getTranslationKey()).getString().toUpperCase();

            int symW = this.textRenderer.getWidth(sym);
            int titleW = this.textRenderer.getWidth(title);

            drawContext.drawText(this.textRenderer, sym, cx - symW / 2, cy - 10, 0xFF000000 | ht.getColor(), true);
            drawContext.drawText(this.textRenderer, title, cx - titleW / 2, cy + 2, 0xFFFFFFFF, true);
        } else {
            // Subtle tactical crosshair when in center deadzone
            drawContext.fill(cx - 5, cy, cx + 6, cy + 1, 0x77FFFFFF);
            drawContext.fill(cx, cy - 5, cx + 1, cy + 6, 0x77FFFFFF);
        }
    }

    private void drawArcSegment(
        VertexConsumer buffer,
        Matrix4f matrix,
        float cx, float cy,
        float rInner, float rOuter,
        double startDeg, double endDeg,
        float r, float g, float b, float a,
        int segments
    ) {
        double stepDeg = (endDeg - startDeg) / segments;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians(startDeg + i * stepDeg);
            double a2 = Math.toRadians(startDeg + (i + 1) * stepDeg);

            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            float cos2 = (float) Math.cos(a2);
            float sin2 = (float) Math.sin(a2);

            float x1 = cx + rInner * cos1;
            float y1 = cy + rInner * sin1;
            float x2 = cx + rOuter * cos1;
            float y2 = cy + rOuter * sin1;
            float x3 = cx + rOuter * cos2;
            float y3 = cy + rOuter * sin2;
            float x4 = cx + rInner * cos2;
            float y4 = cy + rInner * sin2;

            buffer.vertex(matrix, x1, y1, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x2, y2, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x3, y3, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x4, y4, 0.0f).color(r, g, b, a);
        }
    }

    private void drawFilledCircle(
        VertexConsumer buffer,
        Matrix4f matrix,
        float cx, float cy,
        float radius,
        float r, float g, float b, float a,
        int segments
    ) {
        double step = (2.0 * Math.PI) / segments;
        for (int i = 0; i < segments; i++) {
            double a1 = i * step;
            double a2 = (i + 1) * step;

            float x1 = cx + radius * (float) Math.cos(a1);
            float y1 = cy + radius * (float) Math.sin(a1);
            float x2 = cx + radius * (float) Math.cos(a2);
            float y2 = cy + radius * (float) Math.sin(a2);

            buffer.vertex(matrix, cx, cy, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x1, y1, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, x2, y2, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, cx, cy, 0.0f).color(r, g, b, a);
        }
    }

    private void drawRadialLine(
        VertexConsumer buffer,
        Matrix4f matrix,
        float cx, float cy,
        float rInner, float rOuter,
        double angleDeg,
        float thickness,
        float r, float g, float b, float a
    ) {
        double rad = Math.toRadians(angleDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float perpX = -sin * (thickness / 2.0f);
        float perpY = cos * (thickness / 2.0f);

        float x1 = cx + rInner * cos + perpX;
        float y1 = cy + rInner * sin + perpY;
        float x2 = cx + rOuter * cos + perpX;
        float y2 = cy + rOuter * sin + perpY;
        float x3 = cx + rOuter * cos - perpX;
        float y3 = cy + rOuter * sin - perpY;
        float x4 = cx + rInner * cos - perpX;
        float y4 = cy + rInner * sin - perpY;

        buffer.vertex(matrix, x1, y1, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x3, y3, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, x4, y4, 0.0f).color(r, g, b, a);
    }

    /**
     * Confirms the hovered sector and fires the ping.
     */
    public void confirmAndClose() {
        if (hoveredIndex >= 0 && hoveredIndex < OPTIONS.length) {
            PingPointClient.triggerPing(OPTIONS[hoveredIndex].type());
        }
        this.close();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click confirms selection
            confirmAndClose();
            return true;
        } else if (button == 1) { // Right click cancels
            this.close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // If ping is bound to mouse button (e.g. Middle Click) and was released
        if (PingPointClient.getPingKey().matchesMouse(button)) {
            confirmAndClose();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // If the ping key was released while in the radial menu
        if (PingPointClient.getPingKey().matchesKey(keyCode, scanCode)) {
            confirmAndClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }
}
