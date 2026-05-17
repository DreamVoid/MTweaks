package me.dreamvoid.mtweaks.activities;

import android.graphics.drawable.Drawable;

/**
 * 复选列表条目。
 */
public final class CheckListItem {

    /** 条目携带的原始数据。 */
    public Object data;
    /** 左侧图标。 */
    public Drawable icon;
    /** 标题。 */
    public CharSequence title;
    /** 副标题。 */
    public CharSequence description;
    /** 是否勾选。 */
    public boolean checked;
}
