package com.battlelinesystem.network.packet;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PacketGameOverResult extends PacketBase {
    public final String winner;
    public final int scoreA, scoreB;
    public final String teamAName, teamBName;
    public final List<PlayerStatEntry> stats;

    public PacketGameOverResult() {
        this.winner = "";
        this.scoreA = 0;
        this.scoreB = 0;
        this.teamAName = "";
        this.teamBName = "";
        this.stats = new ArrayList<>();
    }

    public PacketGameOverResult(FriendlyByteBuf buf) {
        this.winner = buf.readUtf();
        this.scoreA = buf.readVarInt();
        this.scoreB = buf.readVarInt();
        this.teamAName = buf.readUtf();
        this.teamBName = buf.readUtf();
        int size = buf.readVarInt();
        this.stats = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stats.add(new PlayerStatEntry(
                    buf.readUUID(), buf.readUtf(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
    }

    public PacketGameOverResult(String winner, int scoreA, int scoreB,
                                String teamAName, String teamBName,
                                List<PlayerStatEntry> stats) {
        this.winner = winner;
        this.scoreA = scoreA;
        this.scoreB = scoreB;
        this.teamAName = teamAName;
        this.teamBName = teamBName;
        this.stats = stats;
    }

    public String getWinner() { return winner; }
    public int getScoreA() { return scoreA; }
    public int getScoreB() { return scoreB; }
    public String getTeamAName() { return teamAName; }
    public String getTeamBName() { return teamBName; }
    public List<PlayerStatEntry> getStats() { return stats; }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(winner);
        buf.writeVarInt(scoreA);
        buf.writeVarInt(scoreB);
        buf.writeUtf(teamAName);
        buf.writeUtf(teamBName);
        buf.writeVarInt(stats.size());
        for (PlayerStatEntry e : stats) {
            buf.writeUUID(e.uuid);
            buf.writeUtf(e.name);
            buf.writeUtf(e.team);
            buf.writeVarInt(e.captures);
            buf.writeVarInt(e.kills);
            buf.writeVarInt(e.deaths);
        }
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(8, this));
        return true;
    }

    public static class PlayerStatEntry {
        public final UUID uuid;
        public final String name, team;
        public final int captures, kills, deaths;
        public PlayerStatEntry(UUID uuid, String name, String team,
                               int captures, int kills, int deaths) {
            this.uuid = uuid;
            this.name = name;
            this.team = team;
            this.captures = captures;
            this.kills = kills;
            this.deaths = deaths;
        }
    }
}
