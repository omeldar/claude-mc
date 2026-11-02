package com.omeldar.claudecraft;

import java.util.ArrayList;
import java.util.List;

/** A set of commands Claude suggests, awaiting the player's approval. */
public final class Proposal {
    public String summary = "";
    public List<Step> commands = new ArrayList<>();

    /** Free text Claude wrote alongside the commands. Not part of the wire format. */
    public transient String prose = "";
    /** The Claude Code session this came from, if any. */
    public transient String sessionId = "";

    public static final class Step {
        public String cmd = "";
        public String note = "";
        public transient boolean ran = false;

        public Step() {}

        public Step(String cmd, String note) {
            this.cmd = cmd;
            this.note = note;
        }
    }

    public boolean isEmpty() {
        return commands == null || commands.isEmpty();
    }

    public int size() {
        return commands == null ? 0 : commands.size();
    }
}
