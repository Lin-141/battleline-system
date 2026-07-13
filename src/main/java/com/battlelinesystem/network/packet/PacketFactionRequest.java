package com.battlelinesystem.network.packet;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.faction.FactionManager;
import com.battlelinesystem.network.AllPackets;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

public class PacketFactionRequest extends PacketBase {

    public PacketFactionRequest() {}

    public PacketFactionRequest(FriendlyByteBuf buf) {
        buf.readBoolean();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(true);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        BattleLineSystem.LOGGER.info("PacketFactionRequest.handle called");
        context.enqueueWork(() -> {
            try {
                FactionManager mgr = FactionManager.getInstance();
                BattleLineSystem.LOGGER.info("PacketFactionRequest enqueueWork: {} factions", mgr.getAllFactions().size());
                AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> context.getSender()),
                        new PacketFactionList(mgr.getAllFactions()));
            } catch (Exception e) {
                BattleLineSystem.LOGGER.error("PacketFactionRequest error", e);
            }
        });
        return true;
    }
}
