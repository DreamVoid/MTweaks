package me.dreamvoid.mtweaks.xposed.core;

/**
 * Per-process context handed to every {@link FeatureModule}.
 * Replaces the legacy {@code XC_LoadPackage.LoadPackageParam}.
 */
public record ModuleContext(String packageName, ClassLoader classLoader, boolean systemServer) {
}
