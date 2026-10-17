package io.github.ffdvdgd.opluskeymap;

import android.view.KeyEvent;

final class FunctionKeys {
    static boolean contains(int code) {
        return code >= KeyEvent.KEYCODE_F1 && code <= KeyEvent.KEYCODE_F12;
    }

    static String label(int code) {
        return contains(code) ? "F" + (code - KeyEvent.KEYCODE_F1 + 1) : null;
    }

    static Integer code(String label) {
        for (int code = KeyEvent.KEYCODE_F1; code <= KeyEvent.KEYCODE_F12; code++) {
            if (label(code).equals(label)) return code;
        }
        return null;
    }
}
