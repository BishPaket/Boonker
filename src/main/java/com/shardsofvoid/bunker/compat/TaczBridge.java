package com.shardsofvoid.bunker.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.BunkerMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Мягкая связь с TACZ через его публичный API (TimelessAPI и строители предметов) — без зависимости при сборке.
 * Берёт оружие, патроны и обвесы из всех загруженных ганпаков. Нет TACZ или API изменился — пустой результат.
 */
public final class TaczBridge {
    private TaczBridge() {}

    private static boolean failed;

    public static boolean available() {
        return !failed && ModList.get().isLoaded("tacz");
    }

    private static Class<?> cls(String name) throws ClassNotFoundException {
        return Class.forName(name, true, TaczBridge.class.getClassLoader());
    }

    private static void fail(Throwable t) {
        if (!failed) BunkerMod.LOGGER.warn("TACZ API недоступен, оружие в лут не попадёт: {}", t.toString());
        failed = true;
    }

    @SuppressWarnings("unchecked")
    private static Collection<Map.Entry<ResourceLocation, Object>> index(String method) throws ReflectiveOperationException {
        Object set = cls("com.tacz.guns.api.TimelessAPI").getMethod(method).invoke(null);
        return (Collection<Map.Entry<ResourceLocation, Object>>) set;
    }

    /** Оружие типа type (pistol, smg, rifle, shotgun, dmr, mg, sniper, rpg). */
    public static List<ResourceLocation> gunsOfType(String type) {
        List<ResourceLocation> out = new ArrayList<>();
        if (!available()) return out;
        try {
            for (Map.Entry<ResourceLocation, Object> e : index("getAllCommonGunIndex")) {
                Object t = e.getValue().getClass().getMethod("getType").invoke(e.getValue());
                if (type.equalsIgnoreCase(String.valueOf(t))) out.add(e.getKey());
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            fail(ex);
        }
        return out;
    }

    public static ItemStack gun(ResourceLocation id, RandomSource random) {
        if (!available()) return ItemStack.EMPTY;
        try {
            Object idx = cls("com.tacz.guns.api.TimelessAPI").getMethod("getCommonGunIndex", ResourceLocation.class).invoke(null, id);
            int mag = 0;
            Object fireMode = null;
            if (idx instanceof java.util.Optional<?> opt && opt.isPresent()) {
                Object data = opt.get().getClass().getMethod("getGunData").invoke(opt.get());
                mag = (int) data.getClass().getMethod("getAmmoAmount").invoke(data);
                List<?> modes = (List<?>) data.getClass().getMethod("getFireModeSet").invoke(data);
                if (modes != null && !modes.isEmpty()) fireMode = modes.get(0);
            }
            Class<?> builderCls = cls("com.tacz.guns.api.item.builder.GunItemBuilder");
            Object b = builderCls.getMethod("create").invoke(null);
            b = builderCls.getMethod("setId", ResourceLocation.class).invoke(b, id);
            b = builderCls.getMethod("setAmmoCount", int.class).invoke(b, mag > 0 ? random.nextInt(mag + 1) : 0);
            if (fireMode != null) {
                b = builderCls.getMethod("setFireMode", cls("com.tacz.guns.api.item.gun.FireMode")).invoke(b, fireMode);
            }
            return (ItemStack) builderCls.getMethod("build").invoke(b);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            fail(ex);
            return ItemStack.EMPTY;
        }
    }

    @Nullable
    private static ResourceLocation randomKey(String method, RandomSource random) throws ReflectiveOperationException {
        List<ResourceLocation> keys = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Object> e : index(method)) keys.add(e.getKey());
        return keys.isEmpty() ? null : keys.get(random.nextInt(keys.size()));
    }

    /** Боеприпасы гранатомётов: патроны всех стволов типа rpg (ракеты, 40-мм гранаты и т. п.). */
    private static java.util.Set<ResourceLocation> launcherAmmo() throws ReflectiveOperationException {
        java.util.Set<ResourceLocation> out = new java.util.HashSet<>();
        for (Map.Entry<ResourceLocation, Object> e : index("getAllCommonGunIndex")) {
            Object idx = e.getValue();
            if (!"rpg".equalsIgnoreCase(String.valueOf(idx.getClass().getMethod("getType").invoke(idx)))) continue;
            Object data = idx.getClass().getMethod("getGunData").invoke(idx);
            Object ammo = data.getClass().getMethod("getAmmoId").invoke(data);
            if (ammo instanceof ResourceLocation rl) out.add(rl);
        }
        return out;
    }

    /** Случайные патроны: launcher = false — любой калибр, кроме гранатомётного; true — только гранатомётные. */
    public static ItemStack randomAmmo(RandomSource random, int count, boolean launcher) {
        if (!available()) return ItemStack.EMPTY;
        try {
            java.util.Set<ResourceLocation> heavy = launcherAmmo();
            List<ResourceLocation> keys = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Object> e : index("getAllCommonAmmoIndex")) {
                if (heavy.contains(e.getKey()) == launcher) keys.add(e.getKey());
            }
            ResourceLocation id = keys.isEmpty() ? null : keys.get(random.nextInt(keys.size()));
            if (id == null) return ItemStack.EMPTY;
            Class<?> c = cls("com.tacz.guns.api.item.builder.AmmoItemBuilder");
            Object b = c.getMethod("create").invoke(null);
            b = c.getMethod("setId", ResourceLocation.class).invoke(b, id);
            b = c.getMethod("setCount", int.class).invoke(b, Math.max(1, count));
            return (ItemStack) c.getMethod("build").invoke(b);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            fail(ex);
            return ItemStack.EMPTY;
        }
    }

    /** Случайный обвес — от магазина до тепловизора. */
    public static ItemStack randomAttachment(RandomSource random) {
        if (!available()) return ItemStack.EMPTY;
        try {
            ResourceLocation id = randomKey("getAllCommonAttachmentIndex", random);
            if (id == null) return ItemStack.EMPTY;
            Class<?> c = cls("com.tacz.guns.api.item.builder.AttachmentItemBuilder");
            Object b = c.getMethod("create").invoke(null);
            b = c.getMethod("setId", ResourceLocation.class).invoke(b, id);
            return (ItemStack) c.getMethod("build").invoke(b);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            fail(ex);
            return ItemStack.EMPTY;
        }
    }

    @SuppressWarnings("unused")
    private static Method unused;
}
