package com.battlelinesystem.network.packet;

import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import com.battlelinesystem.network.NetworkManager;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public class PacketCapturePointProgress extends PacketBase {
    public final List<CaptureEntry> entries;
    public final int scoreA;
    public final int scoreB;
    /** 游戏时限（分钟），0=不限时 */
    public final int timeLimitMinutes;
    /** 已流逝秒数 */
    public final int elapsedSeconds;
    /** 时间到后的胜利规则 */
    public final String timeUpRule;

    public PacketCapturePointProgress() {
        this.entries = new ArrayList<>();
        this.scoreA = 0;
        this.scoreB = 0;
        this.timeLimitMinutes = 0;
        this.elapsedSeconds = 0;
        this.timeUpRule = "";
    }

    public PacketCapturePointProgress(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new CaptureEntry(buf.readUtf(), buf.readFloat(), buf.readByte(), buf.readByte(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }
        this.scoreA = buf.readVarInt();
        this.scoreB = buf.readVarInt();
        this.timeLimitMinutes = buf.readVarInt();
        this.elapsedSeconds = buf.readVarInt();
        this.timeUpRule = buf.readUtf();
    }

    public PacketCapturePointProgress(List<CaptureEntry> entries, int scoreA, int scoreB,
                                      int timeLimitMinutes, int elapsedSeconds, String timeUpRule) {
        this.entries = entries;
        this.scoreA = scoreA;
        this.scoreB = scoreB;
        this.timeLimitMinutes = timeLimitMinutes;
        this.elapsedSeconds = elapsedSeconds;
        this.timeUpRule = timeUpRule != null ? timeUpRule : "";
    }

    public List<CaptureEntry> getEntries() { return entries; }
    public int getScoreA() { return scoreA; }
    public int getScoreB() { return scoreB; }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (CaptureEntry e : entries) {
            buf.writeUtf(e.name);
            buf.writeFloat(e.progress);
            buf.writeByte(e.owner);
            buf.writeByte(e.capturing);
            buf.writeVarInt(e.teamACount);
            buf.writeVarInt(e.teamBCount);
            buf.writeBoolean(e.locked);
        }
        buf.writeVarInt(scoreA);
        buf.writeVarInt(scoreB);
        buf.writeVarInt(timeLimitMinutes);
        buf.writeVarInt(elapsedSeconds);
        buf.writeUtf(timeUpRule != null ? timeUpRule : "");
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(6, this));
        return true;
    }

    public static class CaptureEntry {
        public final String name;
        public final float progress;
        public final byte owner; // 0=none, 1=A, 2=B
        public final byte capturing; // 0=none, 1=A, 2=B, 3=contested
        public final int teamACount; // A队在该据点内的玩家数
        public final int teamBCount; // B队在该据点内的玩家数
        public final boolean locked; // 前置据点未满足

        public CaptureEntry(String name, float progress, byte owner, byte capturing,
                            int teamACount, int teamBCount, boolean locked) {
            this.name = name;
            this.progress = progress;
            this.owner = owner;
            this.capturing = capturing;
            this.teamACount = teamACount;
            this.teamBCount = teamBCount;
            this.locked = locked;
        }
    }
}
