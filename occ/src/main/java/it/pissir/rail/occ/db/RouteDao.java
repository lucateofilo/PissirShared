package it.pissir.rail.occ.db;

import it.pissir.rail.occ.traffic.Schedule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tratte dei treni: per ogni treno e direzione (A = andata, R = ritorno) l'elenco ordinato delle fermate. */
public class RouteDao {

    public record Route(int id, String trainId, String direction, List<Schedule.Stop> stops) {
    }

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
                .map(RouteDao::toStop)
                .toList();
        return stops.isEmpty() ? null : new Schedule(stops);
    }

    public List<Route> findAll() {
        List<Map<String, Object>> rows = db.query("""
                SELECT r.id, r.train_id, r.direction, rs.station_id, rs.travel_time_sec, rs.dwell_sec
                FROM route r JOIN route_stop rs ON rs.route_id = r.id
                ORDER BY r.train_id, r.direction, rs.seq""");
        // Raggruppa le righe (una per fermata) in una tratta per ogni id
        Map<Integer, Route> routes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Route route = routes.computeIfAbsent((Integer) row.get("id"), id -> new Route(id,
                    (String) row.get("train_id"), (String) row.get("direction"), new ArrayList<>()));
            route.stops().add(toStop(row));
        }
        return new ArrayList<>(routes.values());
    }

    /** Crea una tratta. Se il treno non esiste ancora viene creato. */
    public void create(String trainId, String direction, List<Schedule.Stop> stops) {
        db.inTransaction(conn -> {
            try (PreparedStatement st = db.prepare(conn,
                    "INSERT INTO train (id) VALUES (?) ON CONFLICT (id) DO NOTHING", trainId)) {
                st.executeUpdate();
            }
            int routeId;
            try (PreparedStatement st = db.prepare(conn,
                    "INSERT INTO route (train_id, direction) VALUES (?, ?) RETURNING id", trainId, direction);
                 ResultSet rs = st.executeQuery()) {
                rs.next();
                routeId = rs.getInt(1);
            }
            insertStops(conn, routeId, stops);
        });
    }

    /** Sostituisce le fermate di una tratta. Restituisce false se la tratta non esiste. */
    public boolean updateStops(int routeId, List<Schedule.Stop> stops) {
        if (db.query("SELECT id FROM route WHERE id = ?", routeId).isEmpty()) {
            return false;
        }
        db.inTransaction(conn -> {
            try (PreparedStatement st = db.prepare(conn, "DELETE FROM route_stop WHERE route_id = ?", routeId)) {
                st.executeUpdate();
            }
            insertStops(conn, routeId, stops);
        });
        return true;
    }

    /** Elimina una tratta con le sue fermate. Restituisce false se la tratta non esiste. */
    public boolean delete(int routeId) {
        return db.update("DELETE FROM route WHERE id = ?", routeId) == 1;
    }

    private void insertStops(Connection conn, int routeId, List<Schedule.Stop> stops) throws SQLException {
        for (int i = 0; i < stops.size(); i++) {
            Schedule.Stop stop = stops.get(i);
            try (PreparedStatement st = db.prepare(conn, """
                            INSERT INTO route_stop (route_id, seq, station_id, travel_time_sec, dwell_sec)
                            VALUES (?, ?, ?, ?, ?)""",
                    routeId, i + 1, stop.stationId(), stop.travelTimeSec(), stop.dwellSec())) {
                st.executeUpdate();
            }
        }
    }

    private static Schedule.Stop toStop(Map<String, Object> row) {
        return new Schedule.Stop((String) row.get("station_id"),
                (Integer) row.get("travel_time_sec"), (Integer) row.get("dwell_sec"));
    }
}
