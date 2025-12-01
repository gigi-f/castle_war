package com.castlewar.debug;

import com.castlewar.entity.Team;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AiDebugLog {
    private final List<Entry> buffer = new ArrayList<>();
    private boolean enabled = true;

    public static final class Entry {
        private final long timestampMillis;
        private final Team team;
        private final String guardType;
        private final String state;
        private final String category;
        private final String detail;

        public Entry(long ts, Team team, String guardType, String state, String category, String detail) {
            this.timestampMillis = ts;
            this.team = team;
            this.guardType = guardType;
            this.state = state;
            this.category = category;
            this.detail = detail;
        }

        public long timestampMillis() { return timestampMillis; }
        public Team team() { return team; }
        public String guardType() { return guardType; }
        public String state() { return state; }
        public String category() { return category; }
        public String detail() { return detail; }
        public String details() { return detail; }
        public com.badlogic.gdx.math.Vector3 target() { return null; }
    }

    public AiDebugLog() {}

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { enabled = e; }

    public void logGuardEvent(Object who, String category, String detail, Object pos, Object dest) {
        buffer.add(new Entry(System.currentTimeMillis(), null, null, "GUARD", category, detail));
        if (buffer.size() > 200) buffer.remove(0);
    }

    public void logAssassinEvent(Object who, String category, String detail, Object pos, Object dest) {
        buffer.add(new Entry(System.currentTimeMillis(), null, "ASSASSIN", "", category, detail));
        if (buffer.size() > 200) buffer.remove(0);
    }

    public List<Entry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(buffer));
    }
}
