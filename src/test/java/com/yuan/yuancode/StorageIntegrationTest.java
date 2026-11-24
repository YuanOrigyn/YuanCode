package com.yuan.yuancode;

import com.yuan.yuancode.config.DatabaseType;
import com.yuan.yuancode.config.PluginConfig;
import com.yuan.yuancode.storage.ReserveRequest;
import com.yuan.yuancode.storage.ReserveResult;
import com.yuan.yuancode.storage.SqlCodeStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void reservesAtomicallyAndEnforcesLimits() {
        PluginConfig defaults = com.yuan.yuancode.config.ConfigManager.defaults();
        PluginConfig config = new PluginConfig(
                defaults.configVersion(),
                defaults.settings(),
                new PluginConfig.Database(DatabaseType.SQLITE, new PluginConfig.Sqlite("test.db"),
                        defaults.database().mysql()),
                defaults.hooks(),
                defaults.rewards()
        );
        SqlCodeStorage storage = new SqlCodeStorage(Logger.getLogger("YuanCodeTest"), tempDir, config);
        storage.initialize().join();
        try {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();
            long now = 1_700_000_000_000L;

            ReserveResult first = storage.reserve(request("ONCE", alice, "Alice", "1.1.1.1", now, 1, 1, 0, 0, 0)).join();
            assertInstanceOf(ReserveResult.Success.class, first);
            ReserveResult second = storage.reserve(request("ONCE", bob, "Bob", "2.2.2.2", now, 1, 1, 0, 0, 0)).join();
            assertEquals(ReserveResult.Reason.GLOBAL_LIMIT,
                    ((ReserveResult.Failure) second).reason());
            assertEquals(1, storage.loadGlobalUsage().join().get("ONCE"));

            ReserveResult playerOnceAlice = storage.reserve(
                    request("PERPLAYER", alice, "Alice", "1.1.1.1", now, 0, 1, 0, 0, 0)).join();
            assertInstanceOf(ReserveResult.Success.class, playerOnceAlice);
            ReserveResult playerOnceAliceAgain = storage.reserve(
                    request("PERPLAYER", alice, "Alice", "1.1.1.1", now + 1_000, 0, 1, 0, 0, 0)).join();
            assertEquals(ReserveResult.Reason.PLAYER_LIMIT,
                    ((ReserveResult.Failure) playerOnceAliceAgain).reason());
            ReserveResult playerOnceBob = storage.reserve(
                    request("PERPLAYER", bob, "Bob", "2.2.2.2", now + 1_000, 0, 1, 0, 0, 0)).join();
            assertInstanceOf(ReserveResult.Success.class, playerOnceBob);
            assertEquals(2, storage.loadGlobalUsage().join().get("PERPLAYER"));

            ReserveResult cooldownFirst = storage.reserve(
                    request("COOLDOWN", alice, "Alice", "1.1.1.1", now, 0, 0, 10_000, 0, 0)).join();
            assertInstanceOf(ReserveResult.Success.class, cooldownFirst);
            ReserveResult cooldownSecond = storage.reserve(
                    request("COOLDOWN", alice, "Alice", "1.1.1.1", now + 1_000, 0, 0, 10_000, 0, 0)).join();
            assertEquals(ReserveResult.Reason.COOLDOWN,
                    ((ReserveResult.Failure) cooldownSecond).reason());

            ReserveResult ipFirst = storage.reserve(
                    request("IP", alice, "Alice", "9.9.9.9", now, 10, 10, 0, 1, 86_400_000)).join();
            assertInstanceOf(ReserveResult.Success.class, ipFirst);
            ReserveResult ipSecond = storage.reserve(
                    request("IP", bob, "Bob", "9.9.9.9", now + 1_000, 10, 10, 0, 1, 86_400_000)).join();
            assertEquals(ReserveResult.Reason.IP_LIMIT,
                    ((ReserveResult.Failure) ipSecond).reason());

            assertEquals(1, storage.loadPlayerUsage(alice).join().claims().get("ONCE"));
            assertTrue(storage.loadStats().join().redemptions() >= 3);

            String json = "{\"type\":\"MONEY\",\"id\":\"r1\",\"amount\":10,\"delayMs\":0,\"conditions\":[]}";
            long pendingId = storage.insertPending(alice, "ONCE", json, now + 5_000).join();
            assertTrue(pendingId > 0);
            assertEquals(1, storage.findDuePending(now + 10_000, 10).join().size());
            storage.deletePending(pendingId).join();
            assertEquals(0, storage.findDuePending(now + 10_000, 10).join().size());
        } finally {
            storage.close().join();
        }
    }

    private static ReserveRequest request(String code, UUID uuid, String name, String ip, long now,
                                          int globalLimit, int perPlayerLimit, long cooldown,
                                          int ipClaims, long ipWindow) {
        return new ReserveRequest(code, uuid, name, ip, now, "GLOBAL", globalLimit, perPlayerLimit,
                cooldown, ipClaims, 0, ipWindow, "test-server");
    }
}
