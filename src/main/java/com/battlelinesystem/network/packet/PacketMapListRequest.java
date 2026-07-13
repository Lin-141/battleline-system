package com.battlelinesystem.network.packet;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.PacketBase;
import com.battlelinesystem.world.GameWorldManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class PacketMapListRequest extends PacketBase {

    public PacketMapListRequest() {}

    public PacketMapListRequest(FriendlyByteBuf buf) {
        buf.readBoolean();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(true);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            try {
                MinecraftServer server = context.getSender().getServer();
                List<PacketMapListResponse.MapEntry> entries = new ArrayList<>();
                for (GameWorldManager.MapInfo info : GameWorldManager.getMapsForMode(server, "")) {
                    entries.add(PacketMapListResponse.MapEntry.from(info));
                }
                AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> context.getSender()),
                        new PacketMapListResponse(entries));
            } catch (Exception e) {
                BattleLineSystem.LOGGER.error("PacketMapListRequest error", e);
            }
        });
        return true;
    }
}
