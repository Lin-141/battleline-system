package com.battlelinesystem.faction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 阵营配置 — 一个阵营的数据模型
 */
public class FactionConfig {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String id;
    public String name;

    @SerializedName("display_color")
    public String displayColor = "#FFFFFF";

    public String description = "";

    /** 可选职业列表 */
    public List<ClassConfig> classes = new ArrayList<>();

    /** 载具池 */
    @SerializedName("vehicles")
    public List<VehicleConfig> vehicles = new ArrayList<>();

    /** 指挥官部署时额外给予的物品 NBT 列表 */
    @SerializedName("commander_extra_items")
    public List<String> commanderExtraItems = new ArrayList<>();

    /** 宽松重生点：部署时可选中同阵营队友直接部署在其位置 */
    @SerializedName("loose_spawn")
    public boolean looseSpawn = false;

    /** 占领据点时播放的音效 (资源路径，如 battlelinesystem:cntake) */
    @SerializedName("capture_sound")
    public String captureSound;

    /** 据点被占时播放的音效 (资源路径，如 battlelinesystem:cnlose) */
    @SerializedName("lose_sound")
    public String loseSound;

    /** 是否有缩略图（运行时检测文件是否存在，不持久化到 JSON） */
    public transient boolean hasThumbnail;

    public FactionConfig() {}

    public FactionConfig(String id, String name, String displayColor, String description) {
        this.id = id;
        this.name = name;
        this.displayColor = displayColor;
        this.description = description;
    }

    /** 深拷贝（不含 transient 字段） */
    public FactionConfig(FactionConfig other) {
        this.id = other.id;
        this.name = other.name;
        this.displayColor = other.displayColor;
        this.description = other.description;
        this.hasThumbnail = other.hasThumbnail;
        if (other.classes != null) {
            for (ClassConfig c : other.classes) {
                this.classes.add(new ClassConfig(c));
            }
        }
        if (other.vehicles != null) {
            for (VehicleConfig v : other.vehicles) {
                this.vehicles.add(new VehicleConfig(v));
            }
        }
        if (other.commanderExtraItems != null) {
            this.commanderExtraItems = new ArrayList<>(other.commanderExtraItems);
        }
        this.looseSpawn = other.looseSpawn;
        this.captureSound = other.captureSound;
        this.loseSound = other.loseSound;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FactionConfig f)) return false;
        return Objects.equals(id, f.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
