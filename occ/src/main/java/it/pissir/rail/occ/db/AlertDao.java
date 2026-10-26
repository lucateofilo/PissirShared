package it.pissir.rail.occ.db;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class AlertDao {

    private final Database db;

    public AlertDao(Database db) {
        this.db = db;
    }

    public void insert(String message) {
        db.update("INSERT INTO alert (message, ts) VALUES (?, ?)", message, Timestamp.from(Instant.now()));
    }

    public List<Map<String, Object>> findLatest(int limit) {
        return db.query("SELECT id, message, ts FROM alert ORDER BY ts DESC LIMIT ?", limit);
    }
}
