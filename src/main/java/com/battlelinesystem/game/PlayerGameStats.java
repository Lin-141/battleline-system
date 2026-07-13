package com.battlelinesystem.game;

import java.util.UUID;

/**
 * 单局玩家战绩 DTO
 */
public class PlayerGameStats {
    public final UUID uuid;
    public String name;
    public String team;
    public int captures;
    public int kills;
    public int deaths;

    public PlayerGameStats(UUID uuid, String name, String team) {
        this.uuid = uuid;
        this.name = name;
        this.team = team;
    }
}
