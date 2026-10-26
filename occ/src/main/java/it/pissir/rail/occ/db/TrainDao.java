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
                SELECT id, state, direction, last_station, next_station, expected_arrival, delay_sec
                FROM train ORDER BY id""");
    }

    /** Ora di partenza dalla stazione di origine del viaggio in corso, oppure null. */
    public Instant findJourneyStart(String trainId) {
        List<Map<String, Object>> rows = db.query("SELECT journey_start FROM train WHERE id = ?", trainId);
        if (rows.isEmpty() || rows.get(0).get("journey_start") == null) {
            return null;
        }
        return ((Timestamp) rows.get(0).get("journey_start")).toInstant();
    }

    public void startJourney(String trainId, Instant when) {
        db.update("UPDATE train SET journey_start = ? WHERE id = ?", Timestamp.from(when), trainId);
    }

    /** Il treno è entrato in una stazione. */
    public void arrivedAt(String trainId, String stationId, String direction, Integer delaySec) {
        db.update("""
                UPDATE train SET state = 'IN_STATION', direction = ?, last_station = ?, next_station = NULL,
                                 expected_arrival = NULL, delay_sec = COALESCE(?, delay_sec)
                WHERE id = ?""", direction, stationId, delaySec, trainId);
    }

    /** Il treno è uscito da una stazione; expectedArrival è l'arrivo stimato alla prossima. */
    public void departedFrom(String trainId, String stationId, String direction, Integer delaySec, Instant expectedArrival) {
        db.update("""
                UPDATE train SET state = 'RUNNING', direction = ?, last_station = ?,
                                 expected_arrival = ?, delay_sec = COALESCE(?, delay_sec)
                WHERE id = ?""",
                direction, stationId, expectedArrival == null ? null : Timestamp.from(expectedArrival), delaySec, trainId);
    }

    public void setNextStation(String trainId, String nextStation) {
        db.update("UPDATE train SET next_station = ? WHERE id = ?", nextStation, trainId);
    }
}
