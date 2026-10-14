package org.barrelmc.barrel.network;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2193.BedrockCodecHelper_v2193;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestActionType;

// What the library has wrong of the protocol a server of mojang speaks. It is a subclass of the codec only to get at
// the tables the codec is made of
public final class BedrockCodecFix extends Bedrock_v2193 {

    private BedrockCodecFix() {
    }

    public static BedrockCodec fix(BedrockCodec codec) {
        return codec.toBuilder()
                .helper(() -> new Helper())
                .build();
    }

    private static final class Helper extends BedrockCodecHelper_v2193 {

        private Helper() {
            super(ENTITY_DATA, GAME_RULE_TYPES, ITEM_STACK_REQUEST_TYPES, CONTAINER_SLOT_TYPES, PLAYER_ABILITIES, TEXT_PROCESSING_ORIGINS);
        }

        // An action of an item stack request starts with its kind. The library writes the two deprecated kinds of
        // crafting by numbers of a later protocol, which has a reserved kind before them. A server of this protocol
        // does not know that number, and dropped the connection at every craft, which always tells the server what
        // it made ("invalid enum value")
        @Override
        protected void writeRequestActionData(ByteBuf buffer, ItemStackRequestAction action) {
            int start = buffer.writerIndex();
            super.writeRequestActionData(buffer, action);
            if (action.getType() == ItemStackRequestActionType.CRAFT_NON_IMPLEMENTED_DEPRECATED || action.getType() == ItemStackRequestActionType.CRAFT_RESULTS_DEPRECATED) {
                buffer.setByte(start, buffer.getByte(start) - 1);
            }
        }
    }
}
