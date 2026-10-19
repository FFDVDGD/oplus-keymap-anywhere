package io.github.ffdvdgd.opluskeymap;

import java.util.ArrayList;
import java.util.List;

/** Per-wheel input and animation state. The first wheel remains entirely OEM-managed. */
final class DirectionalWheel {
    enum Direction { TOP, BOTTOM, LEFT, RIGHT, CENTER }
    record Point(float x, float y) {
        Point midpoint(Point other) {
            return new Point((x + other.x) / 2, (y + other.y) / 2);
        }
    }
    record Key(int code, Direction direction, Point point) {}

    interface Backend {
        Point convert(Point point) throws ReflectiveOperationException;
        Point jitter(Point point) throws ReflectiveOperationException;
        float jitterOffset(float offset) throws ReflectiveOperationException;
        Object down(int pointer, Point point, int key) throws ReflectiveOperationException;
        boolean alive(Object contact) throws ReflectiveOperationException;
        void move(Object contact, Point point) throws ReflectiveOperationException;
        void up(Object contact, boolean force) throws ReflectiveOperationException;
        void failed(Exception error);
    }

    interface Scheduler {
        void post(Runnable task);
        void remove(Runnable task);
    }

    private final int pointer;
    private final Point rawCenter;
    private final float fastOffset;
    private final Backend backend;
    private final Scheduler scheduler;
    private final Object lock;
    private final List<Key> pressed = new ArrayList<>();
    private Point center;
    private Object contact;
    private Float lastOffset;
    private Runnable animation;

    DirectionalWheel(int pointer, Point rawCenter, float fastOffset, Backend backend,
            Scheduler scheduler, Object lock) {
        this.pointer = pointer;
        this.rawCenter = rawCenter;
        this.fastOffset = fastOffset;
        this.backend = backend;
        this.scheduler = scheduler;
        this.lock = lock;
    }

    // Input, stop, and animation callbacks all use the same lock as touch reset.
    void down(Key key) throws ReflectiveOperationException {
        pressed.removeIf(value -> value.direction == key.direction);
        pressed.add(key);
        update();
    }

    void up(Direction direction) throws ReflectiveOperationException {
        pressed.removeIf(key -> key.direction == direction);
        // Preserve the OEM modifier-release behavior without sharing its global offset.
        if (direction == Direction.CENTER && lastOffset != null && lastOffset == fastOffset) return;
        if (pressed.stream().noneMatch(key -> key.direction != Direction.CENTER)) {
            cancelAnimation();
            if (backend.alive(contact)) backend.up(contact, false);
            contact = null;
            center = null;
        } else {
            update();
        }
    }

    void stop() throws ReflectiveOperationException {
        cancelAnimation();
        pressed.clear();
        center = null;
        lastOffset = null;
        if (backend.alive(contact)) backend.up(contact, true);
        contact = null;
    }

    boolean isIdle() {
        return pressed.isEmpty() && contact == null && animation == null;
    }

    private void update() throws ReflectiveOperationException {
        List<Key> directions = new ArrayList<>();
        for (Key key : pressed) if (key.direction != Direction.CENTER) directions.add(key);
        if (directions.isEmpty()) return;
        Key last = directions.get(directions.size() - 1);
        if (center == null) center = backend.jitter(backend.convert(rawCenter));
        Point aim = backend.convert(last.point);
        Key perpendicular = null;
        for (Key key : directions) {
            if (horizontal(key.direction) != horizontal(last.direction)) perpendicular = key;
        }
        float offset = 5;
        if (perpendicular != null || contains(Direction.CENTER)) {
            lastOffset = modifierOffset();
            offset = lastOffset != null ? lastOffset : perpendicular != null ? 100 : 5;
        }
        if (perpendicular != null) aim = aim.midpoint(backend.convert(perpendicular.point));
        Point origin = backend.convert(rawCenter);
        float dx = aim.x - origin.x;
        float dy = aim.y - origin.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        offset = backend.jitterOffset(offset);
        Point target = backend.jitter(new Point(aim.x + dx / length * offset,
                aim.y + dy / length * offset));

        cancelAnimation();
        if (!backend.alive(contact)) {
            contact = null;
            // Match native behavior: only the first directional key starts a contact.
            if (directions.size() == 1) contact = backend.down(pointer, center, last.code);
        } else {
            backend.move(contact, center);
        }
        if (!backend.alive(contact)) return;
        Object gesture = contact;
        Point start = center;
        animation = new Runnable() {
            private int sample;

            @Override
            public void run() {
                synchronized (lock) {
                    try {
                        // Cancellation alone cannot fence a callback already dequeued by Handler.
                        if (animation != this || !backend.alive(gesture)) return;
                        backend.move(gesture, new Point(start.x + (target.x - start.x) * sample / 12,
                                start.y + (target.y - start.y) * sample / 12));
                        if (++sample < 12) scheduler.post(this);
                        else animation = null;
                    } catch (Exception error) {
                        animation = null;
                        backend.failed(error);
                    }
                }
            }
        };
        scheduler.post(animation);
    }

    private Float modifierOffset() {
        if (pressed.size() == 2) {
            Direction first = pressed.get(0).direction;
            Direction second = pressed.get(1).direction;
            if (first == Direction.CENTER && second == Direction.TOP) return fastOffset;
            if (first == Direction.TOP && second == Direction.CENTER) return 400f;
        } else if (pressed.size() > 2 && contains(Direction.CENTER) && contains(Direction.TOP)
                && !contains(Direction.BOTTOM)
                && (contains(Direction.LEFT) || contains(Direction.RIGHT))) {
            return 400f;
        }
        return null;
    }

    private boolean contains(Direction direction) {
        return pressed.stream().anyMatch(key -> key.direction == direction);
    }

    private static boolean horizontal(Direction direction) {
        return direction == Direction.LEFT || direction == Direction.RIGHT;
    }

    private void cancelAnimation() {
        if (animation != null) scheduler.remove(animation);
        animation = null;
    }
}
