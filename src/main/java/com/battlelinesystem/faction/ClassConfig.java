package com.battlelinesystem.faction;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 职业配置 — 阵营下的可选职业，带装备槽位 + 额外物品 + 变体 + 隐藏显示
 */
public class ClassConfig {

    public String id;
    public String name;

    // 装备槽位（NBT 字符串或物品注册名）— 无变体时作为默认
    public String helmet;
    public String chestplate;
    public String leggings;
    public String boots;

    @SerializedName("off_hand")
    public String offHand;

    // 额外物品（会放入玩家背包，每项为 NBT 字符串）
    @SerializedName("extra_items")
    public List<String> extraItems;

    // 变体列表（非空时玩家选择变体来获取对应装备）
    @SerializedName("variants")
    public List<ClassVariant> variants;

    // 选择界面不显示的物品注册名（如 tacz:ammo_box）
    @SerializedName("hidden_display_items")
    public List<String> hiddenDisplayItems;

    // 选择界面不显示护甲图标（头盔/胸甲/护腿/靴子）
    @SerializedName("hide_armor_icons")
    public boolean hideArmorIcons = true;

    // 枪械改装配件池（按 GunId 匹配）
    @SerializedName("gun_attachment_pools")
    public List<GunAttachmentConfig> gunAttachmentPools;

    /** 最大玩家百分比（0=不限，50=不超过总人数50%） */
    @SerializedName("max_players")
    public int maxPlayers = 0;

    /** 死亡扣分 */
    @SerializedName("death_cost")
    public int deathCost = 1;

    public ClassConfig() {}

    public ClassConfig(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public ClassConfig(String id, String name,
                       String helmet, String chestplate, String leggings, String boots,
                       String offHand) {
        this.id = id;
        this.name = name;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.offHand = offHand;
    }

    public ClassConfig(ClassConfig other) {
        this.id = other.id;
        this.name = other.name;
        this.helmet = other.helmet;
        this.chestplate = other.chestplate;
        this.leggings = other.leggings;
        this.boots = other.boots;
        this.offHand = other.offHand;
        if (other.extraItems != null) {
            this.extraItems = new ArrayList<>(other.extraItems);
        }
        if (other.variants != null) {
            this.variants = new ArrayList<>();
            for (ClassVariant v : other.variants) {
                this.variants.add(new ClassVariant(v));
            }
        }
        if (other.hiddenDisplayItems != null) {
            this.hiddenDisplayItems = new ArrayList<>(other.hiddenDisplayItems);
        }
        this.hideArmorIcons = other.hideArmorIcons;
        this.maxPlayers = other.maxPlayers;
        this.deathCost = other.deathCost;
        if (other.gunAttachmentPools != null) {
            this.gunAttachmentPools = new ArrayList<>();
            for (GunAttachmentConfig g : other.gunAttachmentPools) {
                this.gunAttachmentPools.add(new GunAttachmentConfig(g));
            }
        }
    }

    /** 检查物品注册名是否应被隐藏 */
    public boolean isHidden(String registryName) {
        if (registryName == null || registryName.isEmpty()) return false;
        if (hiddenDisplayItems == null) return false;
        for (String h : hiddenDisplayItems) {
            if (registryName.contains(h)) return true;
        }
        return false;
    }
}
