package it.pissir.rail.occ.db;

import it.pissir.rail.occ.traffic.Schedule;

import java.util.List;

public class RouteDao {

    private final Database db;

    public RouteDao(Database db) {
        this.db = db;
    }

    /** Fermate della tratta di un treno in una direzione. Restituisce null se la tratta non esiste. */
    public Schedule findSchedule(String trainId, String direction) {
        List<Schedule.Stop> stops = db.query("""
                        SELECT rs.station_id, rs.travel_time_sec, rs.dwell_sec
                        FROM route r JOIN route_stop rs ON rs.route_id = r.id
                        WHERE r.train_id = ? AND r.direction = ?
                        ORDER BY rs.seq""",
                        trainId, direction)
                .stream()
                .map(row -> new Schedule.Stop(
                        (String) row.get("station_id"),
                        (Integer) row.get("travel_time_sec"),
                        (Integer) row.get("dwell_sec")))
                .toList();
        return stops.isEmpty() ? null : new Schedule(stops);
    }
}
