package io.github.itsgiantcookie.hstglabs.packet;

import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager.EnqueueResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C → S (docs/design.md §6.3 {@code QueueEdit}): a player adds, removes or moves a technology in
 * their queue. The server re-checks everything (permission, HS state; the client never decides),
 * applies it, and answers with a fresh {@link TechSnapshotPayload} for the owner's online members.
 */
public record QueueEditPayload(Op op, ResourceLocation technology, int index) implements CustomPacketPayload {

    public enum Op {
        ADD,
        REMOVE,
        MOVE
    }

    public static final Type<QueueEditPayload> TYPE = new Type<>(HstgLabs.rl("queue_edit"));
    private static final StreamCodec<ByteBuf, Op> OP_CODEC = ByteBufCodecs.idMapper(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal);
    public static final StreamCodec<ByteBuf, QueueEditPayload> STREAM_CODEC = StreamCodec.composite(
          OP_CODEC, QueueEditPayload::op,
          ResourceLocation.STREAM_CODEC, QueueEditPayload::technology,
          ByteBufCodecs.VAR_INT, QueueEditPayload::index,
          QueueEditPayload::new);

    @Override
    public Type<QueueEditPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(TYPE, STREAM_CODEC, QueueEditPayload::handle);
    }

    private static void handle(QueueEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ResearchManager.get().ifPresent(m -> {
            Result result = apply(m, player, payload);
            player.displayClientMessage(Component.literal(result.message()).withStyle(result.ok() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
            TechSnapshotPayload.sendToOwner(m, m.ownerOf(player), player.server.getPlayerList().getPlayers());
        });
    }

    /** What an edit did, with the line the player sees. */
    public record Result(boolean ok, EnqueueResult code, String message) {
    }

    /** Applies an edit for {@code player} with every server-side check (§4.3). Server thread; GameTests call it directly. */
    public static Result apply(ResearchManager m, ServerPlayer player, QueueEditPayload edit) {
        OwnerId owner = m.ownerOf(player);
        ResourceLocation tech = edit.technology();
        return switch (edit.op()) {
            case ADD -> {
                EnqueueResult r = m.enqueueBy(player, tech);
                yield new Result(r == EnqueueResult.ADDED, r, message(r, tech));
            }
            case REMOVE -> {
                if (!m.mayEditQueue(player)) {
                    yield new Result(false, EnqueueResult.NO_PERMISSION, message(EnqueueResult.NO_PERMISSION, tech));
                }
                boolean removed = m.dequeue(owner, tech);
                yield new Result(removed, removed ? EnqueueResult.ADDED : EnqueueResult.UNKNOWN_TECHNOLOGY,
                      removed ? "Removed " + tech + " from the queue" : tech + " is not queued");
            }
            case MOVE -> {
                if (!m.mayEditQueue(player)) {
                    yield new Result(false, EnqueueResult.NO_PERMISSION, message(EnqueueResult.NO_PERMISSION, tech));
                }
                boolean moved = m.move(owner, tech, edit.index());
                yield new Result(moved, moved ? EnqueueResult.ADDED : EnqueueResult.UNKNOWN_TECHNOLOGY,
                      moved ? "Moved " + tech : tech + " is not queued");
            }
        };
    }

    static String message(EnqueueResult r, ResourceLocation tech) {
        return switch (r) {
            case ADDED -> "Queued " + tech;
            case DUPLICATE -> tech + " is already queued";
            case FULL -> "The queue is full";
            case UNKNOWN_TECHNOLOGY -> "No enabled technology " + tech;
            case ALREADY_RESEARCHED -> tech + " is already researched";
            case VETOED -> "Another mod refused to queue " + tech;
            case NO_PERMISSION -> "Only operators may edit research queues on this server";
            case PREREQUISITES_MISSING -> "Research the prerequisites of " + tech + " first";
            case REQUIREMENTS_UNMET -> "The requirements of " + tech + "'s stage are not met yet";
        };
    }
}
