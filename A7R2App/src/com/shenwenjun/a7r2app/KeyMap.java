package com.shenwenjun.a7r2app;

import com.sony.scalar.sysutil.ScalarInput;

/**
 * 把索尼相机的扫描码翻译成名字。
 * 这些常量都是 static final int，编译期就被内联了，
 * 所以本类在没有索尼 API 的模拟器上也不会崩。
 */
public final class KeyMap {

    private KeyMap() {
    }

    public static String name(int scanCode) {
        if (scanCode == ScalarInput.ISV_KEY_S1_1) return "S1_1_half_press";
        if (scanCode == ScalarInput.ISV_KEY_S1_2) return "S1_2";
        if (scanCode == ScalarInput.ISV_KEY_S2) return "S2_shutter";
        if (scanCode == ScalarInput.ISV_KEY_SK1) return "SK1";
        if (scanCode == ScalarInput.ISV_KEY_SK2) return "SK2";
        if (scanCode == ScalarInput.ISV_KEY_DELETE) return "DELETE";
        if (scanCode == ScalarInput.ISV_KEY_ENTER) return "ENTER";
        if (scanCode == ScalarInput.ISV_KEY_MENU) return "MENU";
        if (scanCode == ScalarInput.ISV_KEY_UP) return "UP";
        if (scanCode == ScalarInput.ISV_KEY_DOWN) return "DOWN";
        if (scanCode == ScalarInput.ISV_KEY_LEFT) return "LEFT";
        if (scanCode == ScalarInput.ISV_KEY_RIGHT) return "RIGHT";
        if (scanCode == ScalarInput.ISV_KEY_FN) return "FN";
        if (scanCode == ScalarInput.ISV_KEY_AEL) return "AEL";
        if (scanCode == ScalarInput.ISV_KEY_PLAY) return "PLAY";
        if (scanCode == ScalarInput.ISV_KEY_STASTOP) return "MOVIE";
        if (scanCode == ScalarInput.ISV_KEY_CUSTOM1) return "C1";
        if (scanCode == ScalarInput.ISV_KEY_CUSTOM2) return "C2";
        if (scanCode == ScalarInput.ISV_KEY_CUSTOM3) return "C3";
        if (scanCode == ScalarInput.ISV_KEY_LENS_ATTACH) return "LENS_ATTACH";
        if (scanCode == ScalarInput.ISV_DIAL_1_CLOCKWISE) return "DIAL1_CW";
        if (scanCode == ScalarInput.ISV_DIAL_1_COUNTERCW) return "DIAL1_CCW";
        if (scanCode == ScalarInput.ISV_DIAL_2_CLOCKWISE) return "DIAL2_CW";
        if (scanCode == ScalarInput.ISV_DIAL_2_COUNTERCW) return "DIAL2_CCW";
        if (scanCode == ScalarInput.ISV_DIAL_3_CLOCKWISE) return "DIAL3_CW";
        if (scanCode == ScalarInput.ISV_DIAL_3_COUNTERCW) return "DIAL3_CCW";
        if (scanCode == ScalarInput.ISV_KEY_EV_COMPENSATION) return "EV_COMP";
        if (scanCode == ScalarInput.ISV_KEY_SLI_AF_MF_AEL) return "SLI_AF_MF_AEL";
        if (scanCode == ScalarInput.ISV_KEY_AEL_AFMF) return "AEL_AFMF";
        if (scanCode == ScalarInput.ISV_KEY_CUSTOM) return "CUSTOM";
        if (scanCode == ScalarInput.ISV_KEY_AFMF) return "AFMF";
        if (scanCode == ScalarInput.ISV_KEY_MODE_DIAL) return "MODE_DIAL";
        return "scan=" + scanCode;
    }

    /** 读取某个键/拨盘当前状态（真机才有意义，模拟器会抛异常） */
    public static int status(int key) {
        try {
            com.sony.scalar.sysutil.KeyStatus s = ScalarInput.getKeyStatus(key);
            return s == null ? -1 : s.status;
        } catch (Throwable t) {
            return -1;
        }
    }
}
