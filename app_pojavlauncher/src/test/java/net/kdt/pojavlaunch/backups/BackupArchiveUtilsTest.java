package net.kdt.pojavlaunch.backups;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BackupArchiveUtilsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createAndVerifyBackupReturnsSha256AndPayloadCounts() throws Exception {
        File game = temporaryFolder.newFolder("game");
        File skins = temporaryFolder.newFolder("skins");
        File world = new File(game, "saves/TestWorld");
        assertTrue(world.mkdirs());
        write(new File(world, "level.dat"), "world-data");
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        write(new File(mods, "sample.jar"), "mod-data");

        File archive = new File(temporaryFolder.getRoot(), "snapshot.zip");
        BackupArchiveUtils.createBackup(game, skins, "Test profile",
                new HashSet<>(Arrays.asList(BackupArchiveUtils.WORLDS, BackupArchiveUtils.MODS)), archive);

        BackupArchiveUtils.validateBackup(archive);
        BackupArchiveUtils.VerificationResult result = BackupArchiveUtils.verifyBackup(archive);
        assertEquals(2, result.payloadEntries);
        assertEquals((long) "world-data".length() + "mod-data".length(), result.uncompressedBytes);
        assertNotNull(result.sha256);
        assertEquals(64, result.sha256.length());
        assertTrue(result.sha256.matches("[0-9a-f]{64}"));
    }

    @Test
    public void restorePreservesExistingFilesAndUsesConflictName() throws Exception {
        File game = temporaryFolder.newFolder("game");
        File skins = temporaryFolder.newFolder("skins");
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        write(new File(mods, "sample.jar"), "new-mod");

        File archive = new File(temporaryFolder.getRoot(), "snapshot.zip");
        BackupArchiveUtils.createBackup(game, skins, "Test",
                new HashSet<>(Arrays.asList(BackupArchiveUtils.MODS)), archive);

        File restoreGame = temporaryFolder.newFolder("restore-game");
        File restoreSkins = temporaryFolder.newFolder("restore-skins");
        File restoreMods = new File(restoreGame, "mods");
        assertTrue(restoreMods.mkdirs());
        write(new File(restoreMods, "sample.jar"), "keep-this");

        BackupArchiveUtils.RestoreResult result =
                BackupArchiveUtils.restoreBackup(archive, restoreGame, restoreSkins);
        assertEquals(1, result.restoredFiles);
        assertEquals("keep-this", read(new File(restoreMods, "sample.jar")));
        File[] restored = restoreMods.listFiles((dir, name) -> name.startsWith("sample_restored_"));
        assertNotNull(restored);
        assertEquals(1, restored.length);
        assertEquals("new-mod", read(restored[0]));
    }

    @Test
    public void rejectsZipSlipEntry() throws Exception {
        File archive = archiveWithEntry("mods/../outside.txt");
        expectInvalid(archive);
        assertFalse(new File(temporaryFolder.getRoot(), "outside.txt").exists());
    }

    @Test
    public void rejectsUnknownCategory() throws Exception {
        File archive = archiveWithEntry("unknown/file.bin");
        expectInvalid(archive);
    }

    private File archiveWithEntry(String entryName) throws IOException {
        File archive = new File(temporaryFolder.getRoot(), "crafted.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            put(zip, "novacraft-manifest.txt", "NOVACRAFT_BACKUP_V1\ncreated=1\n");
            put(zip, entryName, "payload");
        }
        return archive;
    }

    private static void put(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void write(File file, String value) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String read(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) break;
                offset += count;
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static void expectInvalid(File archive) throws Exception {
        try {
            BackupArchiveUtils.validateBackup(archive);
            fail("Expected invalid archive to be rejected.");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
