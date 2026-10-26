package it.pissir.rail.occ.db;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class TrainDao {

    private final Database db;

    public TrainDao(Database db) {
        this.db = db;
    }

    public List<Map<String, Object>> findAll() {
        return db.query("""
                SELECT id, state, direction, last_station, next_station
                FROM train ORDER BY id""");
    }

    /** Il treno è entrato in una stazione. */
    public void arrivedAt(String trainId, String stationId, String direction) {
        db.update("""
                UPDATE train SET state = 'IN_STATION', direction = ?, last_station = ?, next_station = NULL
                WHERE id = ?""", direction, stationId, trainId);
    }

    /** Il treno è uscito da una stazione. */
    public void departedFrom(String trainId, String stationId, String direction, Instant when) {
        db.update("""
                UPDATE train SET state = 'RUNNING', direction = ?, last_station = ?, departed_at = ?
                WHERE id = ?""", direction, stationId, Timestamp.from(when), trainId);
    }

    public void setNextStation(String trainId, String nextStation) {
        db.update("UPDATE train SET next_station = ? WHERE id = ?", nextStation, trainId);
    }
}
