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
        return db.query("SELECT id, name, state, detail, last_heartbeat FROM station ORDER BY id");
    }

    public void updateState(String stationId, String state, String detail) {
        db.update("UPDATE station SET state = ?, detail = ? WHERE id = ?", state, detail, stationId);
    }

    public void updateHeartbeat(String stationId) {
        db.update("UPDATE station SET last_heartbeat = ? WHERE id = ?", Timestamp.from(Instant.now()), stationId);
    }

    /** All'avvio della centrale lo stato delle stazioni non è noto finché non si fanno sentire. */
    public void markAllOffline() {
        db.update("UPDATE station SET state = 'OFFLINE', detail = NULL");
    }
}
