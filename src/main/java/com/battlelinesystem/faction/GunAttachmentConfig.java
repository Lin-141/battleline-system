package com.battlelinesystem.faction;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 某把枪可改装的配件池（按槽位分类）。
 * gunId 匹配 tacz NBT 中的 GunId 字段，如 "tacz:g36k"。
 */
public class GunAttachmentConfig {
    @SerializedName("gun_id")
    public String gunId;

    @SerializedName("scopes")
    public List<GunAttachmentOption> scopes;
    @SerializedName("muzzles")
    public List<GunAttachmentOption> muzzles;
    @SerializedName("grips")
    public List<GunAttachmentOption> grips;
    @SerializedName("stocks")
    public List<GunAttachmentOption> stocks;
    @SerializedName("lasers")
    public List<GunAttachmentOption> lasers;

    public GunAttachmentConfig() {}

    public GunAttachmentConfig(String gunId) {
        this.gunId = gunId;
    }

    public GunAttachmentConfig(GunAttachmentConfig other) {
        this.gunId = other.gunId;
        if (other.scopes != null) { this.scopes = new ArrayList<>(); for (var o : other.scopes) this.scopes.add(new GunAttachmentOption(o)); }
        if (other.muzzles != null) { this.muzzles = new ArrayList<>(); for (var o : other.muzzles) this.muzzles.add(new GunAttachmentOption(o)); }
        if (other.grips != null) { this.grips = new ArrayList<>(); for (var o : other.grips) this.grips.add(new GunAttachmentOption(o)); }
        if (other.stocks != null) { this.stocks = new ArrayList<>(); for (var o : other.stocks) this.stocks.add(new GunAttachmentOption(o)); }
        if (other.lasers != null) { this.lasers = new ArrayList<>(); for (var o : other.lasers) this.lasers.add(new GunAttachmentOption(o)); }
    }

    /** 按槽位名返回对应列表 */
    public List<GunAttachmentOption> getBySlot(String slot) {
        return switch (slot) {
            case "scope" -> scopes;
            case "muzzle" -> muzzles;
            case "grip" -> grips;
            case "stock" -> stocks;
            case "laser" -> lasers;
            default -> null;
        };
    }

    public static final String[] SLOTS = {"scope", "muzzle", "grip", "stock", "laser"};
    public static final String[] SLOT_LABELS = {"瞄具", "枪口", "握把", "枪托", "激光"};

    /** Tacz NBT 中的 Attachment 键名映射 */
    public static String nbtKey(String slot) {
        return switch (slot) {
            case "scope" -> "AttachmentSCOPE";
            case "muzzle" -> "AttachmentMUZZLE";
            case "grip" -> "AttachmentGRIP";
            case "stock" -> "AttachmentSTOCK";
            case "laser" -> "AttachmentLASER";
            default -> "";
        };
    }
}
