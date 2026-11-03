package com.omeldar.lumi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Carries a player's settings and notes over from when this mod was called ClaudeMC.
 *
 * <p>Nothing is overwritten: each move only happens when the new location does not exist yet.
 */
public final class LegacyMigration {
    /**
     * Rewritten by the mod as needed, so they are not worth keeping. That includes session.txt:
     * Claude Code files sessions under the working directory, so an id from the old folder
     * could not be resumed from the new one.
     */
    private static final String[] REGENERATED = {
        "session.txt", "mcp.json", "bridge.json", "contract.md", "CLAUDE.md"
    };

    private LegacyMigration() {}

    /** Returns true if anything was moved. */
    public static boolean run(Path configDir, Path gameDir) throws IOException {
        boolean moved = false;

        Path oldConfig = configDir.resolve("claudemc.json");
        Path newConfig = configDir.resolve("lumi.json");
        if (Files.isRegularFile(oldConfig) && !Files.exists(newConfig)) {
            Files.move(oldConfig, newConfig);
            moved = true;
        }

        Path oldWorkspace = gameDir.resolve("claudemc");
        Path newWorkspace = gameDir.resolve("lumi");
        if (Files.isDirectory(oldWorkspace) && !Files.exists(newWorkspace)) {
            Files.move(oldWorkspace, newWorkspace);
            for (String name : REGENERATED) {
                Files.deleteIfExists(newWorkspace.resolve(name));
            }
            moved = true;
        }
        return moved;
    }
}
