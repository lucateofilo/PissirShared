package it.pissir.rail.occ.db;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class StationDao {

    private final Database db;

    public StationDao(Database db) {
        this.db = db;
    }

    public List<Map<String, Object>> findAll() {
        return db.query("SELECT id, name, state, last_heartbeat FROM station ORDER BY id");
    }

    public void updateState(String stationId, String state) {
        db.update("UPDATE station SET state = ? WHERE id = ?", state, stationId);
    }

    public void updateHeartbeat(String stationId) {
        db.update("UPDATE station SET last_heartbeat = ? WHERE id = ?", Timestamp.from(Instant.now()), stationId);
    }
}
