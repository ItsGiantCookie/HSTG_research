package com.hstg.research.recipe;

import com.hstg.research.Registration;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Research lab recipe: one input item + energy over time produces data (into the lab's buffer)
 * and a leftover item (into the output slot).
 *
 * <pre>{@code
 * { "type": "hstg_research:research", "ingredient": {...}, "result": {...},
 *   "data": 250, "energy_per_tick": 50, "duration": 100 }
 * }</pre>
 */
public record ResearchRecipe(Ingredient ingredient, ItemStack result, long data, long energyPerTick, int duration)
      implements Recipe<SingleRecipeInput> {

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    /** Not a crafting-table recipe: keeps it out of the vanilla recipe book. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Registration.RESEARCH_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return Registration.RESEARCH_RECIPE_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<ResearchRecipe> {

        private static final Codec<Long> NON_NEGATIVE_LONG = Codec.LONG.validate(value ->
              value >= 0 ? DataResult.success(value) : DataResult.error(() -> "Value must be >= 0: " + value));

        private static final MapCodec<ResearchRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
              Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(ResearchRecipe::ingredient),
              ItemStack.OPTIONAL_CODEC.optionalFieldOf("result", ItemStack.EMPTY).forGetter(ResearchRecipe::result),
              NON_NEGATIVE_LONG.fieldOf("data").forGetter(ResearchRecipe::data),
              NON_NEGATIVE_LONG.fieldOf("energy_per_tick").forGetter(ResearchRecipe::energyPerTick),
              ExtraCodecs.POSITIVE_INT.fieldOf("duration").forGetter(ResearchRecipe::duration)
        ).apply(instance, ResearchRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, ResearchRecipe> STREAM_CODEC = StreamCodec.composite(
              Ingredient.CONTENTS_STREAM_CODEC, ResearchRecipe::ingredient,
              ItemStack.OPTIONAL_STREAM_CODEC, ResearchRecipe::result,
              ByteBufCodecs.VAR_LONG, ResearchRecipe::data,
              ByteBufCodecs.VAR_LONG, ResearchRecipe::energyPerTick,
              ByteBufCodecs.VAR_INT, ResearchRecipe::duration,
              ResearchRecipe::new);

        @Override
        public MapCodec<ResearchRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ResearchRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
