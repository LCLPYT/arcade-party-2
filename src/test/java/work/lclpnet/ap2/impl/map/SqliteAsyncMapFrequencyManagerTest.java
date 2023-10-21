package work.lclpnet.ap2.impl.map;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.ap2.base.ArcadeParty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqliteAsyncMapFrequencyManagerTest {

    private static final Logger logger = LoggerFactory.getLogger(SqliteAsyncMapFrequencyManagerTest.class);

    @Test
    void open_empty_succeeds() throws Exception {
        Path dir = Files.createTempDirectory("ap2");
        Path dbFile = dir.resolve("ap.sqlite");

        assertFalse(Files.exists(dbFile));

        try (var manager = new SqliteAsyncMapFrequencyManager(dbFile, logger)) {
            manager.open().join();
        }
    }

    @Test
    void open_existing_succeeds() throws Exception {
        Path dir = Files.createTempDirectory("ap2");
        Path dbFile = dir.resolve("ap.sqlite");

        try (var manager = new SqliteAsyncMapFrequencyManager(dbFile, logger)) {
            manager.open().join();
        }

        assertTrue(Files.exists(dbFile));

        try (var manager = new SqliteAsyncMapFrequencyManager(dbFile, logger)) {
            manager.open().join();
        }
    }

    @Test
    void preload_empty_doesNotFail() throws Exception {
        Path dir = Files.createTempDirectory("ap2");
        Path dbFile = dir.resolve("ap.sqlite");

        try (var manager = new SqliteAsyncMapFrequencyManager(dbFile, logger)) {
            manager.open().join();

            manager.preload(List.of(ArcadeParty.identifier("foo"), ArcadeParty.identifier("bar"))).join();
        }
    }

    @Test
    void write_arbitrary_doesNotFail() throws Exception {
        Path dir = Files.createTempDirectory("ap2");
        Path dbFile = dir.resolve("ap.sqlite");

        try (var manager = new SqliteAsyncMapFrequencyManager(dbFile, logger)) {
            manager.open().join();

            Identifier id = ArcadeParty.identifier("foo");
            manager.setFrequencyInternal(id, 5);
            manager.write(id);
        }
    }

    @Test
    void preload_notEmpty_loaded() throws Exception {
        Path dir = Files.createTempDirectory("ap2");
        Path dbFile = dir.resolve("ap.sqlite");

        Identifier id = ArcadeParty.identifier("foo");

        try (var manager = new SqliteAsyncMapFrequencyManager(dbFile, logger)) {
            manager.open().join();

            manager.setFrequencyInternal(id, 5);
            manager.write(id);
        }

        try (var manager = new SqliteAsyncMapFrequencyManager(dbFile, logger)) {
            manager.open().join();

            manager.preload(List.of(id)).join();

            assertEquals(5, manager.getFrequency(id));
        }
    }
}