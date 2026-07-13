package com.battlelinesystem.network.packet;

import com.battlelinesystem.network.NetworkManager;
import com.battlelinesystem.network.PacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

public class PacketLooseSpawnTest extends PacketBase {
    public final boolean clear; // true=清除所有
    public final double x, y, z;
    public final UUID uuid;
    public final String team;

    public PacketLooseSpawnTest() {
        this.clear = true;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.uuid = null;
        this.team = null;
    }

    public PacketLooseSpawnTest(FriendlyByteBuf buf) {
        this.clear = buf.readBoolean();
        if (!clear) {
            this.x = buf.readDouble();
            this.y = buf.readDouble();
            this.z = buf.readDouble();
            this.uuid = buf.readUUID();
            this.team = buf.readUtf();
        } else {
            this.x = 0;
            this.y = 0;
            this.z = 0;
            this.uuid = null;
            this.team = null;
        }
    }

    /** 添加测试队友 */
    public PacketLooseSpawnTest(double x, double y, double z, UUID uuid, String team) {
        this.clear = false;
        this.x = x;
        this.y = y;
        this.z = z;
        this.uuid = uuid;
        this.team = team;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(clear);
        if (!clear) {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeUUID(uuid);
            buf.writeUtf(team);
        }
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> NetworkManager.dispatchClient(12, this));
        return true;
    }
}
