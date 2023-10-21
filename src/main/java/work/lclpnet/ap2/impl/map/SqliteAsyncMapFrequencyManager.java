package work.lclpnet.ap2.impl.map;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.map.MapFrequencyManager;

import java.nio.file.Path;
import java.sql.*;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * A {@link MapFrequencyManager} backed by an in-memory storage.
 * Changes are asynchronously written to a sqlite database.
 * At initialization, the changes must be fetched.
 */
public class SqliteAsyncMapFrequencyManager extends AsyncMapFrequencyManager implements AutoCloseable {

    private static final String MAP_FREQUENCIES_TABLE = "map_frequencies";
    public static final String MAP_FREQUENCIES_ID = "id";
    public static final String MAP_FREQUENCIES_FREQUENCY = "frequency";
    private final Path databasePath;
    private final Logger logger;
    private Connection connection = null;

    public SqliteAsyncMapFrequencyManager(Path databasePath, Logger logger) {
        super(logger);
        this.databasePath = databasePath;
        this.logger = logger;
    }

    @Override
    protected void read(Collection<Identifier> mapIds) {
        runReported(() -> {
            //noinspection SqlSourceToSinkFlow
            PreparedStatement statement = connection.prepareStatement("SELECT `%s`, `%s` FROM `%s` WHERE `%s` IN (%s)"
                    .formatted(MAP_FREQUENCIES_ID, MAP_FREQUENCIES_FREQUENCY, MAP_FREQUENCIES_TABLE, MAP_FREQUENCIES_ID,
                            mapIds.stream().map(id -> "?").collect(Collectors.joining(","))));

            int i = 1;

            for (Identifier mapId : mapIds) {
                statement.setString(i++, mapId.toString());
            }

            ResultSet result;
            if (!statement.execute() || (result = statement.getResultSet()) == null) {
                logger.error("Sqlite query for map frequencies did not return an ResultSet");
                return;
            }

            while (result.next()) {
                String idStr = result.getString(1);
                long count = result.getLong(2);

                Identifier mapId = new Identifier(idStr);

                setFrequencyInternal(mapId, count);
            }
        });
    }

    @Override
    protected void write(Identifier mapId) {
        runReported(() -> {
            long frequency = getFrequency(mapId);
            if (frequency == 0) return;

            PreparedStatement statement = connection.prepareStatement("REPLACE INTO `%s` (`%s`, `%s`) VALUES (?, ?)"
                    .formatted(MAP_FREQUENCIES_TABLE, MAP_FREQUENCIES_ID, MAP_FREQUENCIES_FREQUENCY));

            statement.setString(1, mapId.toString());
            statement.setLong(2, frequency);

            statement.execute();
        });
    }

    private void setupDatabase() throws SQLException {
        Statement statement = connection.createStatement();
        statement.execute("CREATE TABLE IF NOT EXISTS `%s` (`%s` varchar(64) PRIMARY KEY, `%s` bigint)"
                .formatted(MAP_FREQUENCIES_TABLE, MAP_FREQUENCIES_ID, MAP_FREQUENCIES_FREQUENCY));
    }

    public CompletableFuture<Void> open() {
        return CompletableFuture.runAsync(() -> {
            try {
                this.openSync();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void openSync() throws SQLException {
        String path = databasePath.toUri().toString();

        logger.info("Connecting to sqlite database at {}", path);

        connection = DriverManager.getConnection("jdbc:sqlite:".concat(path));

        logger.info("SQLite connection established");

        setupDatabase();
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }

    private void runReported(SqlTask task) {
        if (connection == null) {
            throw new IllegalStateException("Database not connected");
        }

        try {
            task.run();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private interface SqlTask {
        void run() throws SQLException;
    }
}
