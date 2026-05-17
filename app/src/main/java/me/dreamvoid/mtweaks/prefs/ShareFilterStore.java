package me.dreamvoid.mtweaks.prefs;

import android.os.SystemClock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import me.dreamvoid.mtweaks.utils.Logger;

/**
 * 隐藏分享入口集合的读写入口。
 * <p>
 * 远端偏好在被 hook 的进程中只在加载时读取一次快照，框架的增量更新并不可靠，
 * 因此这里额外把集合镜像到模块共享目录的文件中，hook 进程按需重新读取。
 */
public final class ShareFilterStore {

    public static final String KEY = "key_system_share_filter_activities";
    public static final String FILE_NAME = "share_filter_activities.txt";

    private static final long CACHE_TTL = 1000L;

    private static volatile Set<String> sCached = Set.of();
    private static volatile long sCachedAt = -CACHE_TTL;

    private ShareFilterStore() {
    }

    /**
     * 将集合序列化为一行一个组件名的文本。
     */
    public static String serialize(Set<String> hidden) {
        StringBuilder builder = new StringBuilder();
        for (String item : hidden) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            builder.append(item).append('\n');
        }
        return builder.toString();
    }

    /**
     * 解析序列化文本，空内容视为空集合。
     */
    public static Set<String> parse(String content) {
        if (content == null || content.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(content.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * hook 进程读取当前隐藏集合，最多每秒刷新一次。
     */
    public static Set<String> hidden() {
        long now = SystemClock.elapsedRealtime();
        if (now - sCachedAt < CACHE_TTL) {
            return sCached;
        }
        sCachedAt = now;
        Set<String> hidden = null;
        String content = Prefs.readRemoteFile(FILE_NAME);
        if (content != null) {
            hidden = parse(content);
        }
        if (hidden == null) {
            // 文件不可用时退回远端偏好（可能是加载时的旧快照）
            hidden = Prefs.getStringSet(KEY, Set.of());
            if (hidden == null) {
                hidden = Set.of();
            }
        }
        sCached = hidden;
        return hidden;
    }

    /**
     * 模块进程把集合写入共享文件，供 hook 进程即时读取。
     */
    public static void persist(Set<String> hidden) {
        String content = serialize(hidden);
        if (!PrefsSync.writeFile(FILE_NAME, content)) {
            Logger.w("Failed to mirror " + FILE_NAME);
        }
    }

    /**
     * 从本地偏好补写镜像文件，用于偏好被外部修改后的修复。
     */
    public static void persistFromPrefs() {
        Set<String> hidden = Prefs.getStringSet(KEY, Set.of());
        persist(hidden == null ? Set.of() : new HashSet<>(hidden));
    }
}
