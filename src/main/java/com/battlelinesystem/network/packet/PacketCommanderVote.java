package com.battlelinesystem.network.packet;

import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class PacketCommanderVote extends PacketBase {
    public final String votedPlayerName;

    public PacketCommanderVote() {
        this.votedPlayerName = "";
    }

    public PacketCommanderVote(FriendlyByteBuf buf) {
        this.votedPlayerName = buf.readUtf();
    }

    public PacketCommanderVote(String votedPlayerName) {
        this.votedPlayerName = votedPlayerName;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(votedPlayerName);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return true;
        }
        context.enqueueWork(() -> {
            String team = com.battlelinesystem.game.CapturePointManager.getInstance()
                    .getPlayerTeam(player.getUUID());
            if (team != null) {
                com.battlelinesystem.game.CommanderVoteManager.getInstance()
                        .receiveVote(team, player.getUUID(), votedPlayerName);
            }
        });
        return true;
    }
}
