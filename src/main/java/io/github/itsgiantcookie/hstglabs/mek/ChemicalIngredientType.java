package io.github.itsgiantcookie.hstglabs.mek;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.itsgiantcookie.hstglabs.api.ingredient.IngredientStorage;
import io.github.itsgiantcookie.hstglabs.api.ingredient.IngredientType;
import io.github.itsgiantcookie.hstglabs.api.ingredient.RegisterIngredientTypesEvent;
import java.util.Optional;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.bus.api.IEventBus;

/**
 * {@code hstg_labs:chemical} (docs/design.md §5.8, §8.2, D11): a Mekanism chemical or chemical tag,
 * amount in mB, held in consumer chemical tanks. Mekanism API types only (D22).
 * <pre>{ "type": "hstg_labs:chemical", "chemical": "mekanism:oxygen", "amount": 100 }
 * { "type": "hstg_labs:chemical", "tag": "c:oxygen", "amount": 100 }</pre>
 */
public final class ChemicalIngredientType implements IngredientType<ChemicalIngredientType.ChemicalIngredient, ChemicalStack> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("hstg_labs", "chemical");

    /** Exactly one of {@code chemical} and {@code tag}. */
    public record ChemicalIngredient(Optional<Holder<Chemical>> chemical, Optional<TagKey<Chemical>> tag) {
    }

    private static final MapCodec<ChemicalIngredient> CODEC = RecordCodecBuilder.<ChemicalIngredient>mapCodec(instance -> instance.group(
          Chemical.HOLDER_CODEC.optionalFieldOf("chemical").forGetter(ChemicalIngredient::chemical),
          TagKey.codec(MekanismAPI.CHEMICAL_REGISTRY_NAME).optionalFieldOf("tag").forGetter(ChemicalIngredient::tag)
    ).apply(instance, ChemicalIngredient::new)).validate(c -> c.chemical().isPresent() == c.tag().isPresent()
          ? DataResult.error(() -> "a hstg_labs:chemical ingredient names exactly one of chemical or tag") : DataResult.success(c));

    private static final StreamCodec<RegistryFriendlyByteBuf, ChemicalIngredient> STREAM_CODEC = StreamCodec.composite(
          ByteBufCodecs.BOOL, c -> c.tag().isPresent(),
          ResourceLocation.STREAM_CODEC, c -> c.tag().map(TagKey::location).orElseGet(() -> c.chemical().orElseThrow().unwrapKey().orElseThrow().location()),
          (isTag, id) -> isTag ? new ChemicalIngredient(Optional.empty(), Optional.of(TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, id)))
                : new ChemicalIngredient(MekanismAPI.CHEMICAL_REGISTRY.getHolder(id).map(h -> (Holder<Chemical>) h), Optional.empty()));

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterIngredientTypesEvent.class, e -> e.register(ID, new ChemicalIngredientType()));
    }

    @Override
    public MapCodec<ChemicalIngredient> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, ChemicalIngredient> streamCodec() {
        return STREAM_CODEC;
    }

    @Override
    public IngredientStorage<ChemicalStack> storage() {
        return IngredientStorage.CHEMICAL;
    }

    @Override
    public boolean matches(ChemicalIngredient ingredient, ChemicalStack stored) {
        return ingredient.chemical().map(stored::is).orElseGet(() -> stored.is(ingredient.tag().orElseThrow()));
    }

    @Override
    public Component describe(ChemicalIngredient ingredient) {
        return Component.literal(ingredient.chemical().flatMap(Holder::unwrapKey).map(k -> k.location().toString())
              .orElseGet(() -> "#" + ingredient.tag().orElseThrow().location()));
    }
}
