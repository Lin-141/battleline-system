package com.battlelinesystem.network;

import com.battlelinesystem.BattleLineSystem;
import com.battlelinesystem.network.packet.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor.TargetPoint;

import java.util.function.*;

public enum AllPackets {

    // Client to Server
    SELECT_MODE(PacketSelectMode.class, PacketSelectMode::new, NetworkDirection.PLAY_TO_SERVER),
    SELECT_MAP(PacketSelectMap.class, PacketSelectMap::new, NetworkDirection.PLAY_TO_SERVER),
    FACTION_ACTION(PacketFactionAction.class, PacketFactionAction::new, NetworkDirection.PLAY_TO_SERVER),
    FACTION_REQUEST(PacketFactionRequest.class, PacketFactionRequest::new, NetworkDirection.PLAY_TO_SERVER),
    MAP_LIST_REQUEST(PacketMapListRequest.class, PacketMapListRequest::new, NetworkDirection.PLAY_TO_SERVER),
    FACTION_SELECT(PacketFactionSelect.class, PacketFactionSelect::new, NetworkDirection.PLAY_TO_SERVER),
    MAP_CONFIG_SAVE(PacketMapConfigSave.class, PacketMapConfigSave::new, NetworkDirection.PLAY_TO_SERVER),
    TEAM_SELECT(PacketTeamSelect.class, PacketTeamSelect::new, NetworkDirection.PLAY_TO_SERVER),
    CLASS_SELECT(PacketClassSelect.class, PacketClassSelect::new, NetworkDirection.PLAY_TO_SERVER),
    CAPTURE_POINT_VIEW_TELEPORT(PacketCapturePointViewTeleport.class, PacketCapturePointViewTeleport::new, NetworkDirection.PLAY_TO_SERVER),
    CAPTURE_POINT_VIEW_TELEPORT_RAW(PacketCapturePointViewTeleportRaw.class, PacketCapturePointViewTeleportRaw::new, NetworkDirection.PLAY_TO_SERVER),
    COMMANDER_VOTE(PacketCommanderVote.class, PacketCommanderVote::new, NetworkDirection.PLAY_TO_SERVER),
    VIEW_MOVE(PacketViewMove.class, PacketViewMove::new, NetworkDirection.PLAY_TO_SERVER),
        SAVE_GUN_MOD(PacketSaveGunMod.class, PacketSaveGunMod::new, NetworkDirection.PLAY_TO_SERVER),

    // Server to Client
    OPEN_SCREEN(PacketOpenScreen.class, PacketOpenScreen::new, NetworkDirection.PLAY_TO_CLIENT),
    TIME_UP(PacketTimeUp.class, PacketTimeUp::new, NetworkDirection.PLAY_TO_CLIENT),
    FACTION_LIST(PacketFactionList.class, PacketFactionList::new, NetworkDirection.PLAY_TO_CLIENT),
    MAP_LIST_RESPONSE(PacketMapListResponse.class, PacketMapListResponse::new, NetworkDirection.PLAY_TO_CLIENT),
    OPEN_FACTION_VOTE(PacketOpenFactionVote.class, PacketOpenFactionVote::new, NetworkDirection.PLAY_TO_CLIENT),
    OPEN_CLASS_VOTE(PacketOpenClassVote.class, PacketOpenClassVote::new, NetworkDirection.PLAY_TO_CLIENT),
    SYNC_CAPTURE_POINTS(PacketSyncCapturePoints.class, PacketSyncCapturePoints::new, NetworkDirection.PLAY_TO_CLIENT),
    CAPTURE_POINT_PROGRESS(PacketCapturePointProgress.class, PacketCapturePointProgress::new, NetworkDirection.PLAY_TO_CLIENT),
    SYNC_SPAWN_POINTS(PacketSyncSpawnPoints.class, PacketSyncSpawnPoints::new, NetworkDirection.PLAY_TO_CLIENT),
    GAME_OVER_RESULT(PacketGameOverResult.class, PacketGameOverResult::new, NetworkDirection.PLAY_TO_CLIENT),
    SYNC_BEACON_ENTITIES(PacketSyncBeaconEntities.class, PacketSyncBeaconEntities::new, NetworkDirection.PLAY_TO_CLIENT),
    OPEN_COMMANDER_VOTE(PacketOpenCommanderVote.class, PacketOpenCommanderVote::new, NetworkDirection.PLAY_TO_CLIENT),
    LOOSE_SPAWN_TEST(PacketLooseSpawnTest.class, PacketLooseSpawnTest::new, NetworkDirection.PLAY_TO_CLIENT),
    SYNC_FORBIDDEN_ZONES(PacketSyncForbiddenZones.class, PacketSyncForbiddenZones::new, NetworkDirection.PLAY_TO_CLIENT),
    SYNC_CLASS_COUNTS(PacketClassCountUpdate.class, PacketClassCountUpdate::new, NetworkDirection.PLAY_TO_CLIENT);

    private static final String NETWORK_VERSION = "1";
    private static SimpleChannel channel;
    private final PacketType<?> packetType;

    <T extends PacketBase> AllPackets(Class<T> type, Function<FriendlyByteBuf, T> factory, NetworkDirection direction) {
        packetType = new PacketType<>(type, factory, direction);
    }

    public static void registerPackets() {
        PacketType.index = 0;
        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(BattleLineSystem.MOD_ID, "main"),
                () -> NETWORK_VERSION,
                NETWORK_VERSION::equals,
                NETWORK_VERSION::equals
        );
        for (AllPackets packet : values())
            packet.packetType.register();
        BattleLineSystem.LOGGER.info("网络管理器：已注册 {} 个数据包", values().length);
    }

    public static SimpleChannel getChannel() { return channel; }

    public static void sendToAll(Object message) {
        channel.send(PacketDistributor.ALL.noArg(), message);
    }

    public static void sendToNear(Level world, BlockPos pos, int range, Object message) {
        channel.send(PacketDistributor.NEAR.with(() -> new TargetPoint(pos.getX(), pos.getY(), pos.getZ(), range, world.dimension())), message);
    }

    private static class PacketType<T extends PacketBase> {
        private static int index = 0;
        private final BiConsumer<T, FriendlyByteBuf> encoder;
        private final Function<FriendlyByteBuf, T> decoder;
        private final BiConsumer<T, Supplier<NetworkEvent.Context>> handler;
        private final Class<T> type;
        private final NetworkDirection direction;

        PacketType(Class<T> type, Function<FriendlyByteBuf, T> factory, NetworkDirection direction) {
            this.encoder = T::write;
            this.decoder = factory;
            this.handler = (packet, ctx) -> {
                NetworkEvent.Context context = ctx.get();
                if (packet.handle(context)) context.setPacketHandled(true);
            };
            this.type = type;
            this.direction = direction;
        }

        void register() {
            channel.messageBuilder(type, index++, direction)
                    .encoder(encoder)
                    .decoder(decoder)
                    .consumerNetworkThread(handler)
                    .add();
        }
    }
}
