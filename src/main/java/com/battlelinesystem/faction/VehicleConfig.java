package com.battlelinesystem.faction;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 载具配置 — 阵营载具池中的一项
 * itemNbt 为物品的 NBT 字符串（用于生成/召唤该载具的物品）
 */
public class VehicleConfig {

    public String id;
    public String name;

    /** 载具对应物品的 NBT 字符串 */
    @SerializedName("item_nbt")
    public String itemNbt;

    /** 载具类型: plane, tank, apc, helicopter, boat, car (用于匹配载具出生点) */
    public String type = "tank";

    /** 最大同时存在数量，0=无限 */
    @SerializedName("max_count")
    public int maxCount = 0;

    /** 部署冷却时间（秒），0=无冷却 */
    @SerializedName("cooldown_seconds")
    public int cooldownSeconds = 0;

    public String description = "";

    /** 部署时执行的命令脚本（支持 @p 占位符指向部署玩家） */
    @SerializedName("deploy_scripts")
    public List<String> deployScripts = new ArrayList<>();

    /** 部署时播放的音频（如 "battlelinesystem:usair"，空则不播放） */
    @SerializedName("deploy_sound")
    public String deploySound = "";

    /** 部署音频目标: "team"=同队, "all"=全体, "enemy"=敌方 */
    @SerializedName("deploy_sound_target")
    public String deploySoundTarget = "all";

    public VehicleConfig() {}

    public VehicleConfig(String id, String name, String itemNbt) {
        this.id = id;
        this.name = name;
        this.itemNbt = itemNbt;
    }

    public VehicleConfig(VehicleConfig other) {
        this.id = other.id;
        this.name = other.name;
        this.itemNbt = other.itemNbt;
        this.type = other.type;
        this.maxCount = other.maxCount;
        this.cooldownSeconds = other.cooldownSeconds;
        this.description = other.description;
        this.deployScripts = other.deployScripts != null ? new ArrayList<>(other.deployScripts) : new ArrayList<>();
        this.deploySound = other.deploySound != null ? other.deploySound : "";
        this.deploySoundTarget = other.deploySoundTarget != null ? other.deploySoundTarget : "all";
    }
}
