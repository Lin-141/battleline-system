package com.battlelinesystem.faction;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 职业变体 — 同一职业下的不同配置。
 * 解锁条件示例: ""=无需, "permission:xxx", "level:10", "purchase:variantId"
 */
public class ClassVariant {

    public String id;
    public String name;

    public String helmet;
    public String chestplate;
    public String leggings;
    public String boots;

    @SerializedName("off_hand")
    public String offHand;

    @SerializedName("extra_items")
    public List<String> extraItems;

    @SerializedName("unlock_condition")
    public String unlockCondition;

    public ClassVariant() {}

    public ClassVariant(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public ClassVariant(ClassVariant other) {
        this.id = other.id;
        this.name = other.name;
        this.helmet = other.helmet;
        this.chestplate = other.chestplate;
        this.leggings = other.leggings;
        this.boots = other.boots;
        this.offHand = other.offHand;
        this.unlockCondition = other.unlockCondition;
        if (other.extraItems != null) {
            this.extraItems = new ArrayList<>(other.extraItems);
        }
    }

    /** 把此变体的装备数据复制到 ClassConfig（用于向后兼容发放） */
    public void copyTo(ClassConfig out) {
        out.helmet = this.helmet;
        out.chestplate = this.chestplate;
        out.leggings = this.leggings;
        out.boots = this.boots;
        out.offHand = this.offHand;
        if (this.extraItems != null)
            out.extraItems = new ArrayList<>(this.extraItems);
        else
            out.extraItems = null;
    }
}
