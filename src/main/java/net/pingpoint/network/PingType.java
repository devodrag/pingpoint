package net.pingpoint.network;

import net.minecraft.text.Text;

public enum PingType {
    ATTENTION("attention", "ping.pingpoint.attention", "◆", 0x00D4FF, 0.0f, 0.83f, 1.0f),
    CAUTION("caution",     "ping.pingpoint.caution",   "⚡", 0xFFFF8800, 1.0f, 0.53f, 0.0f),
    DANGER("danger",       "ping.pingpoint.danger",    "⚠", 0xFFFF3333, 1.0f, 0.2f,  0.2f),
    LOOT("loot",           "ping.pingpoint.loot",      "★", 0xFFFFC700, 1.0f, 0.78f, 0.0f),
    MINE("mine",           "ping.pingpoint.mine",      "⛏", 0xFF22C55E, 0.13f, 0.77f, 0.37f);

    private final String id;
    private final String translationKey;
    private final String symbol;
    private final int color;
    private final float r;
    private final float g;
    private final float b;

    PingType(String id, String translationKey, String symbol, int color, float r, float g, float b) {
        this.id = id;
        this.translationKey = translationKey;
        this.symbol = symbol;
        this.color = color;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public String getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getColor() {
        return color;
    }

    public float getR() {
        return r;
    }

    public float getG() {
        return g;
    }

    public float getB() {
        return b;
    }

    public Text getDisplayText() {
        return Text.translatable(translationKey);
    }
}
