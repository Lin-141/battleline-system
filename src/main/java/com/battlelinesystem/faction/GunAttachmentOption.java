package com.battlelinesystem.faction;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个枪械配件的可用选项
 */
public class GunAttachmentOption {
    @SerializedName("id")
    public String attachmentId;   // 如 "tacz:sight_exp3"

    @SerializedName("name")
    public String displayName;    // 如 "EXP3全息"

    @SerializedName("unlock")
    public String unlockCondition; // 如 "" 或 "level:5"

    public GunAttachmentOption() {}

    public GunAttachmentOption(String id, String name) {
        this.attachmentId = id;
        this.displayName = name;
    }

    public GunAttachmentOption(GunAttachmentOption other) {
        this.attachmentId = other.attachmentId;
        this.displayName = other.displayName;
        this.unlockCondition = other.unlockCondition;
    }
}
