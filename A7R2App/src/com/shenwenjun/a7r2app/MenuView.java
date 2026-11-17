package com.shenwenjun.a7r2app;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

/**
 * 菜单（v4.1，对应 Pixso 03/04/05/06/07）。
 *
 * 结构：顶部选项卡（拍摄 / 设置 / 延时设置 / 预设）+ 面包屑 + 项目列表。
 * 视觉：无选中底色、无圆角，只在选中行左侧放 3px 黄铜竖线；
 * 数值右对齐，选中行的值转黄铜色；行间 5% 白细线。
 *
 * v4.1：行高 26→38，项目名 13→17px，选项卡 9→16px —— 机身 3 寸屏上
 * 原来的字号看不清。所有尺寸都是设计稿像素（640×480 1:1）。
 */
public class MenuView extends View {

    public static final int LEVEL_TAB = 0;
    public static final int LEVEL_GROUP = 1;
    public static final int LEVEL_ITEM = 2;

    private String[] tabs = new String[0];
    private String[] groups = new String[0];
    private String[] labels = new String[0];
    private String[] values = new String[0];

    private int tab;
    private int group;
    private int item;
    private int level = LEVEL_ITEM;
    private boolean editing;

    private static final float TAB_X = 20f;
    private static final float TAB_Y = 9f;
    private static final float TAB_SIZE = 18f;
    private static final float TAB_GAP = 26f;
    private static final float TAB_RULE_Y = 38f;
    private static final float CRUMB_Y = 46f;
    private static final float RULE_Y = 76f;
    private static final float LIST_TOP = 88f;
    private static final float ROW_H = 44f;
    private static final float LEFT = 20f;
    private static final float RIGHT = 620f;

    public MenuView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setState(int tab, int group, int item, int level, boolean editing,
                         String[] tabs, String[] groups, String[] labels, String[] values) {
        this.tab = tab;
        this.group = group;
        this.item = item;
        this.level = level;
        this.editing = editing;
        this.tabs = tabs == null ? new String[0] : tabs;
        this.groups = groups == null ? new String[0] : groups;
        this.labels = labels == null ? new String[0] : labels;
        this.values = values == null ? new String[0] : values;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        Ui.init(getContext());
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        Ui.rect(c, 0f, 0f, w, h, Ui.BG);

        // ---------- 选项卡 ----------
        float x = TAB_X;
        for (int i = 0; i < tabs.length; i++) {
            boolean active = (i == tab);
            float tw = Ui.text(c, tabs[i], x, TAB_Y, TAB_SIZE, Ui.cjk(),
                    active ? Ui.INK : Ui.alpha(Ui.INK, 0.72f));
            if (active) {
                Ui.rect(c, x, TAB_RULE_Y, tw, 2f, Ui.alpha(Ui.INK, 0.75f));
            }
            x += tw + TAB_GAP;
        }
        if (tabs.length > 0) {
            Ui.textRight(c, (tab + 1) + "/" + tabs.length, RIGHT, TAB_Y + 2f, 14f, Ui.num(), Ui.MID);
        }

        // ---------- 面包屑 ----------
        String crumb = tabs.length > tab ? tabs[tab] : "";
        if (groups.length > 0 && group < groups.length) {
            crumb = crumb + "  ·  " + groups[group];
        }
        if (labels.length > 0 && item < labels.length && labels[item] != null) {
            crumb = crumb + "  ·  " + labels[item];
        }
        Ui.text(c, crumb, LEFT, CRUMB_Y, 16f, Ui.cjk(), Ui.MID);
        Ui.rect(c, LEFT, RULE_Y, RIGHT - LEFT, 1f, Ui.alpha(0xFFFFFF, 0.10f));

        // ---------- 项目列表 ----------
        int visible = (int) ((h - LIST_TOP - 40f) / ROW_H);
        if (visible < 1) {
            visible = 1;
        }
        int offset = 0;
        if (item >= visible) {
            offset = item - visible + 1;
        }
        // 列表超出一屏时，在面包屑右侧标出「第几项 / 共几项」，避免以为项目没了
        if (labels.length > visible) {
            Ui.textRight(c, (item + 1) + "/" + labels.length, RIGHT, CRUMB_Y, 15f, Ui.num(), Ui.MID);
        }
        for (int i = offset; i < labels.length && i - offset < visible; i++) {
            float y = LIST_TOP + (i - offset) * ROW_H;
            boolean active = (i == item);
            if (active) {
                Ui.rect(c, LEFT - 4f, y + 2f, 3f, ROW_H - 4f, Ui.BRASS);
            }
            Ui.text(c, labels[i] == null ? "" : labels[i], LEFT + 6f, y + 12f, 20f, Ui.cjk(),
                    active ? Ui.INK : Ui.alpha(Ui.INK, 0.80f));
            String v = (i < values.length && values[i] != null) ? values[i] : "";
            if (v.length() > 0) {
                Ui.textRight(c, v, RIGHT, y + 13f, 20f, Ui.cjk(), active ? Ui.BRASS : Ui.alpha(Ui.INK, 0.88f));
            }
            if (i < labels.length - 1) {
                Ui.rect(c, LEFT + 4f, y + ROW_H - 1f, RIGHT - LEFT - 8f, 1f,
                        Ui.alpha(0xFFFFFF, 0.10f));
            }
        }

        // ---------- 底部提示 ----------
        Ui.text(c, editing
                ? "转盘 改值    中央 确认    FN 退出"
                : "左右 选项卡    上下 选项    转盘 改值    中央 执行    FN 退出",
                LEFT, 446f, 14f, Ui.cjk(), Ui.MID);
    }
}
