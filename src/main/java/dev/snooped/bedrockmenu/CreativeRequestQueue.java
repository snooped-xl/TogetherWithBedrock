// SPDX-License-Identifier: GPL-3.0-or-later
package dev.snooped.bedrockmenu;

import java.util.ArrayDeque;

/** Optimistic UI gestures, but only one request may cross the network before its acknowledgement. */
final class CreativeRequestQueue<T> {
    record Entry<T>(int sequence, T value) { }
    record Completion<T>(boolean matched, Entry<T> next) { }
    private final ArrayDeque<Entry<T>> waiting = new ArrayDeque<>();
    private Entry<T> active;
    boolean pending() { return active != null; }
    int size() { return waiting.size() + (active == null ? 0 : 1); }
    Entry<T> offer(int sequence, T value) {
        if (size() >= 64) throw new IllegalStateException("Creative inventory is waiting for the server");
        Entry<T> entry = new Entry<>(sequence, value);
        if (active == null) { active = entry; return entry; }
        waiting.addLast(entry); return null;
    }
    Completion<T> complete(int sequence, boolean accepted) {
        if (active == null || active.sequence() != sequence) return new Completion<>(false, null);
        active = null;
        if (accepted) active = waiting.pollFirst(); else waiting.clear();
        return new Completion<>(true, active);
    }
    void clear() { active = null; waiting.clear(); }
}
