package it.pissir.rail.occ.db;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Guasti permanenti. Stato: OPEN (segnalato), DISPATCHED (operatori inviati), RESOLVED (riparato). */
public class FaultDao {

    private final Database db;

    public FaultDao(Database db) {
        this.db = db;
    }

    /** Guasti non ancora riparati. */
    public List<Map<String, Object>> findActive() {
        return db.query("""
                SELECT id, station_id, kind, status, opened_at, dispatched_at, dispatched_by
                FROM fault WHERE status <> 'RESOLVED' ORDER BY opened_at""");
    }

    /** Stazione del guasto se è ancora in stato OPEN, altrimenti null. */
    public String findOpenStation(int faultId) {
        List<Map<String, Object>> rows = db.query(
                "SELECT station_id FROM fault WHERE id = ? AND status = 'OPEN'", faultId);
        return rows.isEmpty() ? null : (String) rows.get(0).get("station_id");
    }

    public void open(String stationId, String kind) {
        db.update("INSERT INTO fault (station_id, kind, opened_at) VALUES (?, ?, ?)",
                stationId, kind, Timestamp.from(Instant.now()));
    }

    public void markDispatched(int faultId, String username) {
        db.update("UPDATE fault SET status = 'DISPATCHED', dispatched_at = ?, dispatched_by = ? WHERE id = ?",
                Timestamp.from(Instant.now()), username, faultId);
    }

    public void resolve(String stationId) {
        db.update("UPDATE fault SET status = 'RESOLVED', resolved_at = ? WHERE station_id = ? AND status <> 'RESOLVED'",
                Timestamp.from(Instant.now()), stationId);
    }
}
