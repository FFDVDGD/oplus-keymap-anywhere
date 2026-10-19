package io.github.ffdvdgd.opluskeymap;

import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

final class ShortcutKeys {
    enum Action { PASS, CONSUME, RELEASE, RESET }

    private final Map<Long, Integer> pressed = new HashMap<>();

    Action handle(InputEvent event, int releaseKey, int resetKey, BooleanSupplier enabled) {
        int code;
        int family;
        boolean down;
        boolean first;
        if (event instanceof KeyEvent) {
            KeyEvent key = (KeyEvent) event;
            if (key.getAction() != KeyEvent.ACTION_DOWN && key.getAction() != KeyEvent.ACTION_UP) {
                return Action.PASS;
            }
            code = key.getKeyCode();
            family = 1;
            down = key.getAction() == KeyEvent.ACTION_DOWN;
            first = key.getRepeatCount() == 0;
        } else if (event instanceof MotionEvent) {
            MotionEvent mouse = (MotionEvent) event;
            code = mouseKey(mouse);
            family = 2;
            down = mouse.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS;
            first = true;
        } else {
            return Action.PASS;
        }
        if (code == KeyEvent.KEYCODE_UNKNOWN) return Action.PASS;
        long token = ((long) event.getDeviceId() << 32) | (code & 0xffffffffL);
        int owned = pressed.getOrDefault(token, 0);
        if (owned != 0) {
            // A side button may emit both event families. Trigger once and retain
            // ownership through both releases, even after settings/scope change.
            int remaining = down ? owned | family : owned & ~family;
            if (remaining == 0) pressed.remove(token);
            else pressed.put(token, remaining);
            return Action.CONSUME;
        }
        if ((code != releaseKey && code != resetKey) || !enabled.getAsBoolean()) return Action.PASS;
        if (!down) return Action.CONSUME;
        pressed.put(token, family);
        if (!first) return Action.CONSUME;
        // If the user binds both operations to one key, full reset takes priority.
        return code == resetKey ? Action.RESET : Action.RELEASE;
    }

    boolean isIdle() {
        return pressed.isEmpty();
    }

    void clear() {
        pressed.clear();
    }

    static int mouseKey(MotionEvent event) {
        int action = event.getActionMasked();
        if ((action != MotionEvent.ACTION_BUTTON_PRESS && action != MotionEvent.ACTION_BUTTON_RELEASE)
                || (!event.isFromSource(InputDevice.SOURCE_MOUSE)
                && event.getToolType(event.getActionIndex()) != MotionEvent.TOOL_TYPE_MOUSE)) {
            return KeyEvent.KEYCODE_UNKNOWN;
        }
        if (event.getActionButton() == MotionEvent.BUTTON_BACK) return KeyEvent.KEYCODE_BACK;
        if (event.getActionButton() == MotionEvent.BUTTON_FORWARD) return KeyEvent.KEYCODE_FORWARD;
        return KeyEvent.KEYCODE_UNKNOWN;
    }
}
