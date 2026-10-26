package it.pissir.rail.occ.db;

import it.pissir.rail.common.msg.Transit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public class TransitDao {

    private final Database db;

    public TransitDao(Database db) {
        this.db = db;
    }

    /**
     * Salva un transito. Se l'eventId è già presente (messaggio ricevuto due volte)
     * non fa nulla e restituisce false.
     */
    public boolean insert(Transit t) {
        int rows = db.update("""
                INSERT INTO transit (event_id, train_id, station_id, type, ts)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING""",
                UUID.fromString(t.eventId()), t.trainId(), t.stationId(), t.type(),
                Timestamp.from(Instant.parse(t.ts())));
        return rows == 1;
    }
}
