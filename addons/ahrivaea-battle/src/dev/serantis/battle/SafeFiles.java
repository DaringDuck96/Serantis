package dev.serantis.battle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Saving our files in the world folder safely: written to a temporary file first and then swapped in, so a server
 * killed mid-save never leaves a half-written file behind. A file that can't be read is kept aside as ".bad" (see
 * keepBad) before anything saves over it, so nothing is silently lost.
 */
public final class SafeFiles {
    private SafeFiles() {}

    public static void write(Path file, String text) throws java.io.IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void write(Path file, java.util.List<String> lines) throws java.io.IOException {
        write(file, String.join("\n", lines) + (lines.isEmpty() ? "" : "\n"));
    }

    /** A file we couldn't read at all: copy it to "<name>.bad" so the next save doesn't wipe what was in it. */
    public static void keepBad(Path file) {
        try {
            if (Files.exists(file)) Files.copy(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) { }
    }

    /** Lines of a text file, never failing on odd characters. */
    public static java.util.List<String> lines(Path file) throws java.io.IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).lines().toList();
    }
}
