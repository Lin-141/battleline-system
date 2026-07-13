package com.battlelinesystem.network.packet;

import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public class PacketOpenCommanderVote extends PacketBase {
    public final List<String> playerNames;
    public final String team;
    public final int countdownSeconds;

    public PacketOpenCommanderVote() {
        this.playerNames = new ArrayList<>();
        this.team = "";
        this.countdownSeconds = 0;
    }

    public PacketOpenCommanderVote(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.playerNames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) playerNames.add(buf.readUtf());
        this.team = buf.readUtf();
        this.countdownSeconds = buf.readVarInt();
    }

    public PacketOpenCommanderVote(List<String> playerNames, String team, int countdownSeconds) {
        this.playerNames = playerNames;
        this.team = team;
        this.countdownSeconds = countdownSeconds;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(playerNames.size());
        for (String n : playerNames) buf.writeUtf(n);
        buf.writeUtf(team);
        buf.writeVarInt(countdownSeconds);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(11, this));
        return true;
    }
}
