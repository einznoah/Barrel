package org.barrelmc.barrel.network.translator;

import io.netty.util.ReferenceCountUtil;
import lombok.Getter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.network.translator.java.*;
import org.barrelmc.barrel.network.translator.java.ContainerClosePacket;
import org.barrelmc.barrel.network.translator.java.InteractPacket;
import org.barrelmc.barrel.network.translator.java.PlayerActionPacket;
import org.barrelmc.barrel.network.translator.java.PlayerInputPacket;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerButtonClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundPlaceRecipePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundRenameItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSelectTradePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSeenAdvancementsPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSetCreativeModeSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class PacketTranslatorManager {

    // A single thread, the java client has to receive the translated packets in the order the bedrock server sent them
    private final ExecutorService threadPoolExecutor = Executors.newSingleThreadExecutor();

    @Getter
    private final Map<Class<? extends Packet>, JavaPacketTranslator> javaTranslators = new HashMap<>();
    @Getter
    private final Map<Class<? extends BedrockPacket>, BedrockPacketTranslator> bedrockTranslators = new HashMap<>();

    private final Player player;

    public PacketTranslatorManager(Player player) {
        this.player = player;
        this.registerDefaultPackets();
    }

    public void translate(BedrockPacket pk) {
        BedrockPacketTranslator translator = bedrockTranslators.get(pk.getClass());

        if (translator != null) {
            if (translator.immediate()) {
                translator.translate(pk, player);
            } else {
                // Netty releases the packet as soon as this method returns
                ReferenceCountUtil.retain(pk);
                boolean accepted = this.execute(() -> {
                    try {
                        translator.translate(pk, player);
                    } finally {
                        ReferenceCountUtil.release(pk);
                    }
                });
                if (!accepted) {
                    ReferenceCountUtil.release(pk);
                }
            }
        }
    }

    public void translate(MinecraftPacket pk) {
        JavaPacketTranslator translator = javaTranslators.get(pk.getClass());

        if (translator != null) {
            this.execute(() -> translator.translate(pk, player));
        }
    }

    // Runs after the packets that are being translated, and returns whether it will
    public boolean execute(Runnable translation) {
        try {
            threadPoolExecutor.execute(translation);
            return true;
        } catch (RejectedExecutionException e) {
            // The player disconnected
            return false;
        }
    }

    public void shutdown() {
        threadPoolExecutor.shutdown();
    }

    private void registerDefaultPackets() {
        // Bedrock packets
        bedrockTranslators.put(AddPlayerPacket.class, new org.barrelmc.barrel.network.translator.bedrock.AddPlayerPacket());
        bedrockTranslators.put(AnimatePacket.class, new org.barrelmc.barrel.network.translator.bedrock.AnimatePacket());
        bedrockTranslators.put(BlockEventPacket.class, new org.barrelmc.barrel.network.translator.bedrock.BlockEventPacket());
        bedrockTranslators.put(LevelChunkPacket.class, new org.barrelmc.barrel.network.translator.bedrock.LevelChunkPacket());
        bedrockTranslators.put(LevelEventPacket.class, new org.barrelmc.barrel.network.translator.bedrock.LevelEventPacket());
        bedrockTranslators.put(MoveEntityAbsolutePacket.class, new org.barrelmc.barrel.network.translator.bedrock.MoveEntityAbsolutePacket());
        bedrockTranslators.put(MovePlayerPacket.class, new org.barrelmc.barrel.network.translator.bedrock.MovePlayerPacket());
        bedrockTranslators.put(PlayerListPacket.class, new org.barrelmc.barrel.network.translator.bedrock.PlayerListPacket());
        bedrockTranslators.put(RemoveEntityPacket.class, new org.barrelmc.barrel.network.translator.bedrock.RemoveEntityPacket());
        bedrockTranslators.put(RemoveObjectivePacket.class, new org.barrelmc.barrel.network.translator.bedrock.RemoveObjectivePacket());
        bedrockTranslators.put(ResourcePacksInfoPacket.class, new org.barrelmc.barrel.network.translator.bedrock.ResourcePacksInfoPacket());
        bedrockTranslators.put(ResourcePackStackPacket.class, new org.barrelmc.barrel.network.translator.bedrock.ResourcePackStackPacket());
        bedrockTranslators.put(ServerToClientHandshakePacket.class, new org.barrelmc.barrel.network.translator.bedrock.ServerToClientHandshakePacket());
        bedrockTranslators.put(SetDisplayObjectivePacket.class, new org.barrelmc.barrel.network.translator.bedrock.SetDisplayObjectivePacket());
        bedrockTranslators.put(SetScorePacket.class, new org.barrelmc.barrel.network.translator.bedrock.SetScorePacket());
        bedrockTranslators.put(SetTimePacket.class, new org.barrelmc.barrel.network.translator.bedrock.SetTimePacket());
        bedrockTranslators.put(SyncWorldClocksPacket.class, new org.barrelmc.barrel.network.translator.bedrock.SyncWorldClocksPacket());
        bedrockTranslators.put(StartGamePacket.class, new org.barrelmc.barrel.network.translator.bedrock.StartGamePacket());
        bedrockTranslators.put(TakeItemEntityPacket.class, new org.barrelmc.barrel.network.translator.bedrock.TakeItemEntityPacket());
        bedrockTranslators.put(TextPacket.class, new org.barrelmc.barrel.network.translator.bedrock.TextPacket());
        bedrockTranslators.put(PlayStatusPacket.class, new org.barrelmc.barrel.network.translator.bedrock.PlayStatusPacket());
        bedrockTranslators.put(UpdateBlockPacket.class, new org.barrelmc.barrel.network.translator.bedrock.UpdateBlockPacket());
        bedrockTranslators.put(DisconnectPacket.class, new org.barrelmc.barrel.network.translator.bedrock.DisconnectPacket());
        bedrockTranslators.put(SetPlayerGameTypePacket.class, new org.barrelmc.barrel.network.translator.bedrock.SetPlayerGameTypePacket());
        bedrockTranslators.put(ChangeDimensionPacket.class, new org.barrelmc.barrel.network.translator.bedrock.ChangeDimensionPacket());
        bedrockTranslators.put(SetEntityDataPacket.class, new org.barrelmc.barrel.network.translator.bedrock.SetEntityDataPacket());
        bedrockTranslators.put(SetSpawnPositionPacket.class, new org.barrelmc.barrel.network.translator.bedrock.SetSpawnPositionPacket());
        bedrockTranslators.put(UpdateAbilitiesPacket.class, new org.barrelmc.barrel.network.translator.bedrock.UpdateAbilitiesPacket());
        bedrockTranslators.put(UpdateAttributesPacket.class, new org.barrelmc.barrel.network.translator.bedrock.UpdateAttributesPacket());
        bedrockTranslators.put(NetworkSettingsPacket.class, new org.barrelmc.barrel.network.translator.bedrock.NetworkSettingsPacket());
        bedrockTranslators.put(AddEntityPacket.class, new org.barrelmc.barrel.network.translator.bedrock.AddEntityPacket());
        bedrockTranslators.put(AddItemEntityPacket.class, new org.barrelmc.barrel.network.translator.bedrock.AddItemEntityPacket());
        bedrockTranslators.put(MoveEntityDeltaPacket.class, new org.barrelmc.barrel.network.translator.bedrock.MoveEntityDeltaPacket());
        bedrockTranslators.put(SetEntityMotionPacket.class, new org.barrelmc.barrel.network.translator.bedrock.SetEntityMotionPacket());
        bedrockTranslators.put(MobEquipmentPacket.class, new org.barrelmc.barrel.network.translator.bedrock.MobEquipmentPacket());
        bedrockTranslators.put(MobArmorEquipmentPacket.class, new org.barrelmc.barrel.network.translator.bedrock.MobArmorEquipmentPacket());
        bedrockTranslators.put(EntityEventPacket.class, new org.barrelmc.barrel.network.translator.bedrock.EntityEventPacket());
        bedrockTranslators.put(ItemComponentPacket.class, new org.barrelmc.barrel.network.translator.bedrock.ItemComponentPacket());
        bedrockTranslators.put(CreativeContentPacket.class, new org.barrelmc.barrel.network.translator.bedrock.CreativeContentPacket());
        bedrockTranslators.put(InventoryContentPacket.class, new org.barrelmc.barrel.network.translator.bedrock.InventoryContentPacket());
        bedrockTranslators.put(InventorySlotPacket.class, new org.barrelmc.barrel.network.translator.bedrock.InventorySlotPacket());
        bedrockTranslators.put(ItemStackResponsePacket.class, new org.barrelmc.barrel.network.translator.bedrock.ItemStackResponsePacket());
        bedrockTranslators.put(PlayerHotbarPacket.class, new org.barrelmc.barrel.network.translator.bedrock.PlayerHotbarPacket());
        bedrockTranslators.put(ContainerOpenPacket.class, new org.barrelmc.barrel.network.translator.bedrock.ContainerOpenPacket());
        bedrockTranslators.put(org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket.class, new org.barrelmc.barrel.network.translator.bedrock.ContainerClosePacket());
        bedrockTranslators.put(ContainerSetDataPacket.class, new org.barrelmc.barrel.network.translator.bedrock.ContainerSetDataPacket());
        bedrockTranslators.put(CraftingDataPacket.class, new org.barrelmc.barrel.network.translator.bedrock.CraftingDataPacket());
        bedrockTranslators.put(PlayerEnchantOptionsPacket.class, new org.barrelmc.barrel.network.translator.bedrock.PlayerEnchantOptionsPacket());
        bedrockTranslators.put(MobEffectPacket.class, new org.barrelmc.barrel.network.translator.bedrock.MobEffectPacket());
        bedrockTranslators.put(RespawnPacket.class, new org.barrelmc.barrel.network.translator.bedrock.RespawnPacket());
        bedrockTranslators.put(UpdateTradePacket.class, new org.barrelmc.barrel.network.translator.bedrock.UpdateTradePacket());
        bedrockTranslators.put(UnlockedRecipesPacket.class, new org.barrelmc.barrel.network.translator.bedrock.UnlockedRecipesPacket());
        bedrockTranslators.put(PacketViolationWarningPacket.class, new org.barrelmc.barrel.network.translator.bedrock.PacketViolationWarningPacket());

        // Java packets
        javaTranslators.put(ServerboundChatPacket.class, new ChatPacket());
        javaTranslators.put(ServerboundSetCarriedItemPacket.class, new SetCarriedItemPacket());
        javaTranslators.put(ServerboundMovePlayerPosPacket.class, new MovePlayerPosPacket());
        javaTranslators.put(ServerboundMovePlayerPosRotPacket.class, new MovePlayerPosRotPacket());
        javaTranslators.put(ServerboundMovePlayerRotPacket.class, new MovePlayerRotPacket());
        javaTranslators.put(ServerboundPlayerCommandPacket.class, new PlayerCommandPacket());
        javaTranslators.put(ServerboundPlayerInputPacket.class, new PlayerInputPacket());
        javaTranslators.put(ServerboundPunchPacket.class, new SwingPacket());
        javaTranslators.put(ServerboundClientCommandPacket.class, new ClientCommandPacket());
        javaTranslators.put(ServerboundClientInformationPacket.class, new ClientInformationPacket());
        javaTranslators.put(ServerboundPlayerActionPacket.class, new PlayerActionPacket());
        javaTranslators.put(ServerboundSeenAdvancementsPacket.class, new SeenAdvancementsPacket());
        javaTranslators.put(ServerboundPlayerAbilitiesPacket.class, new PlayerAbilitiesPacket());
        javaTranslators.put(ServerboundContainerClickPacket.class, new ContainerClickPacket());
        javaTranslators.put(ServerboundContainerClosePacket.class, new ContainerClosePacket());
        javaTranslators.put(ServerboundContainerButtonClickPacket.class, new ContainerButtonClickPacket());
        javaTranslators.put(ServerboundRenameItemPacket.class, new RenameItemPacket());
        javaTranslators.put(ServerboundSelectTradePacket.class, new SelectTradePacket());
        javaTranslators.put(ServerboundPlaceRecipePacket.class, new PlaceRecipePacket());
        javaTranslators.put(ServerboundSetCreativeModeSlotPacket.class, new SetCreativeModeSlotPacket());
        javaTranslators.put(ServerboundUseItemOnPacket.class, new UseItemOnPacket());
        javaTranslators.put(ServerboundUseItemPacket.class, new UseItemPacket());
        javaTranslators.put(ServerboundAttackPacket.class, new AttackPacket());
        javaTranslators.put(ServerboundInteractPacket.class, new InteractPacket());
    }
}
