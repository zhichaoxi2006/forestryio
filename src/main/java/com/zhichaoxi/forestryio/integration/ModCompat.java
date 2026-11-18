package com.zhichaoxi.forestryio.integration;

import com.direwolf20.laserio.common.LaserIO;
import forestry.api.ForestryConstants;
import net.neoforged.fml.ModList;

/**
 * Compile-time and runtime checks for the two required mods.
 *
 * <p>Importing {@link LaserIO} and {@link ForestryConstants} here is deliberate: it proves that
 * both dependency jars are on the compile classpath, and keeps every direct reference to their
 * classes in one small class.
 */
public final class ModCompat {
    public static final String LASERIO_MOD_ID = LaserIO.MODID;
    public static final String FORESTRY_MOD_ID = ForestryConstants.MOD_ID;
    public static final String PATCHOULI_MOD_ID = "patchouli";

    private ModCompat() {
    }

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
