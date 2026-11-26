package me.dreamvoid.mtweaks.xposed.google.iconpack;

/**
 * 已安装图标包条目。
 */
public record IconPackInfo(
        String packageName,
        CharSequence label
) {
}
