package com.battlelinesystem.network.packet;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 → 客户端：实时同步职业选择人数和载具状态。
 * 当有人部署后广播给同阵营所有玩家，不重新打开界面，只更新 HUD 数据。
 */
public class PacketClassCountUpdate extends PacketBase {
    public String factionId;
    public int[] classCounts;
    public int[] vehicleCounts;
    public boolean[] vehicleAlive;
    public int[] vehicleCooldownsData;

    public PacketClassCountUpdate() {}

    public PacketClassCountUpdate(FriendlyByteBuf buf) {
        factionId = buf.readUtf();
        int cs = buf.readVarInt();
        classCounts = new int[cs];
        for (int i = 0; i < cs; i++) classCounts[i] = buf.readVarInt();
        int vs = buf.readVarInt();
        vehicleCounts = new int[vs];
        vehicleAlive = new boolean[vs];
        vehicleCooldownsData = new int[vs];
        for (int i = 0; i < vs; i++) vehicleCounts[i] = buf.readVarInt();
        for (int i = 0; i < vs; i++) vehicleAlive[i] = buf.readBoolean();
        for (int i = 0; i < vs; i++) vehicleCooldownsData[i] = buf.readVarInt();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(factionId);
        buf.writeVarInt(classCounts.length);
        for (int c : classCounts) buf.writeVarInt(c);
        buf.writeVarInt(vehicleCounts.length);
        for (int c : vehicleCounts) buf.writeVarInt(c);
        for (boolean a : vehicleAlive) buf.writeBoolean(a);
        for (int cd : vehicleCooldownsData) buf.writeVarInt(cd);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(14, this));
        return true;
    }
}
