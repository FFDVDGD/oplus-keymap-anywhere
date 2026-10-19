package io.github.ffdvdgd.opluskeymap;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.ffdvdgd.opluskeymap.DirectionalWheel.Direction.*;
import static io.github.ffdvdgd.opluskeymap.DirectionalWheel.Key;
import static io.github.ffdvdgd.opluskeymap.DirectionalWheel.Point;
import static org.junit.Assert.*;

public class DirectionalWheelTest {
    @Test
    public void simultaneousWheelsMoveAndReleaseIndependently() throws Exception {
        Touches touches = new Touches();
        Clock clock = new Clock();
        DirectionalWheel a = wheel(-2000, new Point(100, 200), touches, clock, 650);
        DirectionalWheel b = wheel(-2001, new Point(500, 350), touches, clock, 650);
        a.down(new Key(32, RIGHT, new Point(160, 200)));
        b.down(new Key(40, BOTTOM, new Point(500, 440)));
        clock.drain();
        assertPosition(touches.active.get(-2000), 159.58333f, 200);
        assertPosition(touches.active.get(-2001), 500, 437.08333f);
        a.up(RIGHT);
        assertFalse(touches.active.containsKey(-2000));
        assertPosition(touches.active.get(-2001), 500, 437.08333f);
        b.up(BOTTOM);
        assertTrue(touches.active.isEmpty());
    }

    @Test
    public void rotationConvertsBothCenterAndTarget() throws Exception {
        Touches touches = new Touches();
        touches.rotate = true;
        Clock clock = new Clock();
        DirectionalWheel wheel = wheel(-2000, new Point(100, 200), touches, clock, 650);
        wheel.down(new Key(32, RIGHT, new Point(160, 200)));
        clock.drain();
        assertPosition(touches.active.get(-2000), 1000, 159.58333f);
    }

    @Test
    public void lastDirectionWinsEachAxisAndReleaseRestoresPreviousDirection() throws Exception {
        Touches touches = new Touches();
        Clock clock = new Clock();
        DirectionalWheel wheel = wheel(-2000, new Point(100, 200), touches, clock, 650);
        wheel.down(new Key(51, TOP, new Point(100, 140)));
        wheel.down(new Key(32, RIGHT, new Point(160, 200)));
        clock.drain();
        // The diagonal midpoint (130,170) extends 100 pixels along its unit vector.
        assertPosition(touches.active.get(-2000), 192.31815f, 107.68185f);
        wheel.down(new Key(47, BOTTOM, new Point(100, 260)));
        clock.drain();
        assertPosition(touches.active.get(-2000), 192.31815f, 292.31815f);
        wheel.up(BOTTOM);
        clock.drain();
        assertPosition(touches.active.get(-2000), 192.31815f, 107.68185f);
        wheel.up(RIGHT);
        clock.drain();
        assertPosition(touches.active.get(-2000), 100, 140.41667f);
        wheel.up(TOP);
        assertTrue(touches.active.isEmpty());
    }

    @Test
    public void modifierOrderAndRememberedOffsetArePerWheel() throws Exception {
        float[] offsets = {650, 750};
        float[] expectedY = {-450.83333f, -542.5f};
        for (int i = 0; i < offsets.length; i++) {
            Touches touches = new Touches();
            Clock clock = new Clock();
            DirectionalWheel a = wheel(-2000, new Point(100, 200), touches, clock, offsets[i]);
            DirectionalWheel b = wheel(-2001, new Point(100, 200), touches, clock, offsets[i]);
            Key modifier = new Key(59, CENTER, new Point(100, 200));
            Key top = new Key(51, TOP, new Point(100, 140));
            a.down(modifier);
            assertTrue(touches.active.isEmpty());
            a.down(top);
            b.down(top);
            b.down(modifier);
            clock.drain();
            assertPosition(touches.active.get(-2000), 100, expectedY[i]);
            assertPosition(touches.active.get(-2001), 100, -221.66667f);
            b.down(new Key(32, RIGHT, new Point(160, 200)));
            clock.drain();
            assertPosition(touches.active.get(-2001), 386.77249f, -86.77249f);
            b.up(RIGHT);
            clock.drain();
            a.up(CENTER);
            b.up(CENTER);
            clock.drain();
            assertPosition(touches.active.get(-2000), 100, expectedY[i]);
            assertPosition(touches.active.get(-2001), 100, 140.41667f);
            a.up(TOP);
            b.up(TOP);
            assertTrue(touches.active.isEmpty());
        }
    }

    @Test
    public void resetAndSlotReplacementFenceAlreadyDequeuedAnimation() throws Exception {
        Touches touches = new Touches();
        Clock clock = new Clock();
        DirectionalWheel wheel = wheel(-2000, new Point(100, 200), touches, clock, 650);
        wheel.down(new Key(51, TOP, new Point(100, 140)));
        Contact old = touches.active.get(-2000);
        wheel.stop();
        wheel.down(new Key(32, RIGHT, new Point(160, 200)));
        Contact replacement = touches.active.get(-2000);
        clock.drain();
        assertTrue(old.moves.isEmpty());
        assertEquals(12, replacement.moves.size());
        assertPosition(replacement, 159.58333f, 200);
        wheel.stop();

        wheel.down(new Key(51, TOP, new Point(100, 140)));
        Contact detached = touches.active.get(-2000);
        // A native reset can replace the contact with the same numeric pointer/slot.
        Contact nativeReplacement = new Contact(-2000, new Point(700, 900));
        touches.active.put(-2000, nativeReplacement);
        clock.drain();
        wheel.stop();
        assertTrue(detached.moves.isEmpty());
        assertSame(nativeReplacement, touches.active.get(-2000));
        assertPosition(nativeReplacement, 700, 900);
    }

    @Test
    public void reloadRequiresReleasedKeysEvenWithoutAnActiveTouch() throws Exception {
        Touches touches = new Touches();
        Clock clock = new Clock();
        DirectionalWheel wheel = wheel(-2000, new Point(100, 200), touches, clock, 650);
        assertTrue(wheel.isIdle());
        wheel.down(new Key(59, CENTER, new Point(100, 200)));
        assertTrue(touches.active.isEmpty());
        assertFalse(wheel.isIdle());
        wheel.up(CENTER);
        assertTrue(wheel.isIdle());

        wheel.down(new Key(32, RIGHT, new Point(160, 200)));
        assertFalse(wheel.isIdle());
        clock.drain();
        assertFalse(wheel.isIdle());
        wheel.up(RIGHT);
        assertTrue(wheel.isIdle());
        assertTrue(touches.active.isEmpty());
    }

    private static DirectionalWheel wheel(int pointer, Point center, Touches touches,
            Clock clock, float fastOffset) {
        return new DirectionalWheel(pointer, center, fastOffset, touches, clock, clock);
    }

    private static void assertPosition(Contact contact, float x, float y) {
        assertNotNull(contact);
        assertEquals(x, contact.position.x(), 0.001f);
        assertEquals(y, contact.position.y(), 0.001f);
    }

    private static final class Clock implements DirectionalWheel.Scheduler {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void post(Runnable task) { tasks.add(task); }
        // Exercise cancellation after dequeue, when removeCallbacks cannot retract the task.
        public void remove(Runnable task) {}
        void drain() {
            int count = 0;
            while (!tasks.isEmpty()) {
                assertTrue("animation must finish", ++count < 1000);
                tasks.remove().run();
            }
        }
    }

    private static final class Contact {
        final int pointer;
        final List<Point> moves = new ArrayList<>();
        Point position;
        Contact(int pointer, Point position) {
            this.pointer = pointer;
            this.position = position;
        }
    }

    private static final class Touches implements DirectionalWheel.Backend {
        final Map<Integer, Contact> active = new HashMap<>();
        boolean rotate;
        public Point convert(Point point) {
            return rotate ? new Point(1200 - point.y(), point.x()) : point;
        }
        public Point jitter(Point point) { return point; }
        public float jitterOffset(float offset) { return offset; }
        public Object down(int pointer, Point point, int key) {
            Contact contact = new Contact(pointer, point);
            active.put(pointer, contact);
            return contact;
        }
        public boolean alive(Object value) {
            return value instanceof Contact contact && active.get(contact.pointer) == contact;
        }
        public void move(Object value, Point point) {
            Contact contact = (Contact) value;
            assertTrue(alive(contact));
            contact.position = point;
            contact.moves.add(point);
        }
        public void up(Object value, boolean force) {
            Contact contact = (Contact) value;
            assertTrue(alive(contact));
            active.remove(contact.pointer);
        }
        public void failed(Exception error) { throw new AssertionError(error); }
    }
}
