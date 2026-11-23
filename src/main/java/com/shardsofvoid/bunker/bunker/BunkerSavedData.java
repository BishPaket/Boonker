package com.shardsofvoid.bunker.bunker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.shardsofvoid.bunker.BunkerMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * SavedData bunker_data на каждое измерение (раздел 8.1): идентификатор, владелец, позиция ядра,
 * уровень, состояние и комнаты каждого бункера. Зоны и индексы сюда не пишутся.
 */
public final class BunkerSavedData extends SavedData {
    public static final String NAME = "bunker_data";

    public static final Factory<BunkerSavedData> FACTORY = new Factory<>(BunkerSavedData::new, BunkerSavedData::load);

    public final Map<UUID, Bunker> bunkers = new LinkedHashMap<>();

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Bunker b : bunkers.values()) list.add(b.save());
        tag.put("bunkers", list);
        return tag;
    }

    public static BunkerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BunkerSavedData data = new BunkerSavedData();
        ListTag list = tag.getList("bunkers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                Bunker b = Bunker.load(list.getCompound(i));
                data.bunkers.put(b.id, b);
            } catch (RuntimeException e) {
                BunkerMod.LOGGER.error("Не удалось прочитать бункер #{}: {}", i, e.toString());
            }
        }
        return data;
    }
}
