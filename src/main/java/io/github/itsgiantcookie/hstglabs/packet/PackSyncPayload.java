package io.github.itsgiantcookie.hstglabs.packet;

import com.google.gson.JsonObject;
import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.tech.PackDefinition;
import io.github.itsgiantcookie.hstglabs.tech.PackLookup;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S → C: the loaded science pack definitions (docs/design.md §8.2), so clients can name and tint
 * {@code hstg_labs:science_pack} stacks. Sent on login and after every definitions reload. The client
 * only renders what it gets (§6.3).
 */
public record PackSyncPayload(List<Entry> packs) implements CustomPacketPayload {

    /** What a client needs of one pack. */
    public record Entry(ResourceLocation id, String name, int color, int tier) {

        static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
              ResourceLocation.STREAM_CODEC, Entry::id,
              ByteBufCodecs.STRING_UTF8, Entry::name,
              ByteBufCodecs.INT, Entry::color,
              ByteBufCodecs.VAR_INT, Entry::tier,
              Entry::new);
    }

    public static final Type<PackSyncPayload> TYPE = new Type<>(HstgLabs.rl("pack_sync"));
    public static final StreamCodec<ByteBuf, PackSyncPayload> STREAM_CODEC =
          Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(PackSyncPayload::new, PackSyncPayload::packs);

    public PackSyncPayload {
        packs = List.copyOf(packs);
    }

    public static PackSyncPayload of(Collection<PackDefinition> packs) {
        return new PackSyncPayload(packs.stream().map(p -> new Entry(ResourceLocation.parse(p.id()), p.name(), p.color(), p.tier())).toList());
    }

    @Override
    public Type<PackSyncPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(TYPE, STREAM_CODEC, PackSyncPayload::handle);
    }

    private static void handle(PackSyncPayload payload, IPayloadContext context) {
        Map<ResourceLocation, PackDefinition> packs = payload.packs().stream().collect(Collectors.toMap(Entry::id,
              e -> new PackDefinition(e.id().toString(), e.name(), e.color(), e.tier(), Optional.empty(), new JsonObject())));
        PackLookup.setClient(packs);
    }
}
