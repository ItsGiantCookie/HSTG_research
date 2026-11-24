package de.germanrangers.hstglabs.api.owner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Who research belongs to (docs/design.md §5.10): an owner type and that type's key, for example
 * {@code hstg_labs:team} + a team UUID, or {@code hstg_labs:server} + {@code "server"}.
 *
 * @param type owner type id, see {@link OwnerType}
 * @param key  the owner within that type, stable across restarts
 */
@ApiStatus.Experimental
public record OwnerId(ResourceLocation type, String key) {

    /** Codec for saved data and definitions. */
    public static final Codec<OwnerId> CODEC = RecordCodecBuilder.create(instance -> instance.group(
          ResourceLocation.CODEC.fieldOf("type").forGetter(OwnerId::type),
          Codec.STRING.fieldOf("key").forGetter(OwnerId::key)
    ).apply(instance, OwnerId::new));

    public OwnerId {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
    }
}
