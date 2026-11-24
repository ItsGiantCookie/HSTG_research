package io.github.itsgiantcookie.hstglabs.research;

import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.core.ResearchBook;
import io.github.itsgiantcookie.hstglabs.core.ResearchProgress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Research state per owner (docs/design.md §6.2), {@code data/hstg_labs_research.dat} on the
 * overworld. Shape: {@code schema}, {@code owners: [{type, key, queue, progress: [{tech, units, done,
 * in_flight}], blocked, pending}]}. Entries that cannot be read (unknown ids) are kept as written and
 * saved back untouched.
 */
public final class ResearchSavedData extends SavedData {

    public static final String NAME = "hstg_labs_research";
    public static final int SCHEMA = 1;

    private final Map<OwnerId, ResearchBook<ResourceLocation>> books = new LinkedHashMap<>();
    private final List<CompoundTag> unreadable = new ArrayList<>();

    public static ResearchSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ResearchSavedData::new, ResearchSavedData::load, null), NAME);
    }

    /** The owner's book, created empty if new; its queue limit follows {@code queue_max}. */
    public ResearchBook<ResourceLocation> book(OwnerId owner, int queueMax) {
        ResearchBook<ResourceLocation> book = books.computeIfAbsent(owner, o -> {
            setDirty();
            return new ResearchBook<>(queueMax);
        });
        book.setQueueMax(queueMax);
        return book;
    }

    public Map<OwnerId, ResearchBook<ResourceLocation>> books() {
        return books;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schema", SCHEMA);
        ListTag owners = new ListTag();
        books.forEach((owner, book) -> owners.add(write(owner, book)));
        unreadable.forEach(raw -> owners.add(raw.copy()));
        tag.put("owners", owners);
        return tag;
    }

    public static ResearchSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ResearchSavedData data = new ResearchSavedData();
        for (Tag t : tag.getList("owners", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) t;
            ResourceLocation type = ResourceLocation.tryParse(o.getString("type"));
            if (type == null || o.getString("key").isEmpty()) {
                data.unreadable.add(o.copy());
                continue;
            }
            data.books.put(new OwnerId(type, o.getString("key")), read(o));
        }
        return data;
    }

    private static CompoundTag write(OwnerId owner, ResearchBook<ResourceLocation> book) {
        CompoundTag o = new CompoundTag();
        o.putString("type", owner.type().toString());
        o.putString("key", owner.key());
        o.put("queue", ids(book.queue().entries()));
        ListTag progress = new ListTag();
        book.progressEntries().forEach((tech, p) -> {
            CompoundTag e = new CompoundTag();
            e.putString("tech", tech.toString());
            e.putInt("units", p.units());
            e.putInt("done", p.done());
            e.putInt("in_flight", p.inFlight());
            progress.add(e);
        });
        o.put("progress", progress);
        o.put("blocked", ids(book.blockedTechnologies()));
        o.put("pending", ids(book.pending()));
        o.putInt("queue_max", book.queue().max());
        return o;
    }

    private static ResearchBook<ResourceLocation> read(CompoundTag o) {
        Map<ResourceLocation, ResearchProgress> progress = new LinkedHashMap<>();
        for (Tag t : o.getList("progress", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ResourceLocation tech = ResourceLocation.tryParse(e.getString("tech"));
            int units = e.getInt("units");
            int done = e.getInt("done");
            if (tech != null && units >= 1 && done >= 0 && done <= units && e.getInt("in_flight") >= 0) {
                progress.put(tech, ResearchProgress.restore(units, done, e.getInt("in_flight")));
            }
        }
        int queueMax = Math.max(1, o.getInt("queue_max"));
        return ResearchBook.restore(queueMax, readIds(o, "queue"), progress, readIds(o, "blocked"), readIds(o, "pending"));
    }

    private static ListTag ids(Iterable<ResourceLocation> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        return list;
    }

    private static List<ResourceLocation> readIds(CompoundTag o, String key) {
        List<ResourceLocation> out = new ArrayList<>();
        for (Tag t : o.getList(key, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }
}
