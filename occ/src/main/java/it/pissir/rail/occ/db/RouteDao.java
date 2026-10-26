package it.pissir.rail.occ.db;

import java.util.List;
import java.util.Map;

public class RouteDao {

    /** Prossima fermata di un treno. nextStation è null se la stazione attuale è il capolinea. */
    public record NextStop(String nextStation, int travelTimeSec, int dwellSec) {
    }

    private final Database db;

    public RouteDao(Database db) {
        this.db = db;
    }

    /**
     * Cerca la fermata successiva a currentStation nella tratta del treno.
     * Restituisce null se la stazione non fa parte della tratta.
     */
    public NextStop findNextStop(String trainId, String direction, String currentStation) {
        List<Map<String, Object>> rows = db.query("""
                SELECT cur.dwell_sec, nxt.station_id AS next_station, nxt.travel_time_sec
                FROM route r
                JOIN route_stop cur ON cur.route_id = r.id AND cur.station_id = ?
                LEFT JOIN route_stop nxt ON nxt.route_id = r.id AND nxt.seq = cur.seq + 1
                WHERE r.train_id = ? AND r.direction = ?""",
                currentStation, trainId, direction);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Object travel = row.get("travel_time_sec");
        return new NextStop(
                (String) row.get("next_station"),
                travel == null ? 0 : (Integer) travel,
                (Integer) row.get("dwell_sec"));
    }
}
