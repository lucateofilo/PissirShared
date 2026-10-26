package it.pissir.rail.occ.ingestion;

import it.pissir.rail.common.Json;
import it.pissir.rail.common.Mqtt;
import it.pissir.rail.common.Topics;
import it.pissir.rail.common.msg.Transit;
import it.pissir.rail.occ.db.Database;
import it.pissir.rail.occ.db.TrainDao;
import it.pissir.rail.occ.db.TransitDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/** Riceve i transiti dalle stazioni, li salva e aggiorna la posizione dei treni. */
public class Ingestion {

    private static final Logger log = LoggerFactory.getLogger(Ingestion.class);

    private final Mqtt mqtt;
    private final TransitDao transits;
    private final TrainDao trains;

    public Ingestion(Mqtt mqtt, Database db) {
        this.mqtt = mqtt;
        this.transits = new TransitDao(db);
        this.trains = new TrainDao(db);
    }

    public void start() {
        mqtt.subscribe(Topics.ALL_TRANSITS, 1, (topic, payload) -> onTransit(payload));
    }

    private void onTransit(String payload) {
        Transit t = Json.parse(payload, Transit.class);
        if (t.eventId() == null || t.trainId() == null || t.stationId() == null || t.ts() == null) {
            log.warn("Transito incompleto, scartato: {}", payload);
            return;
        }
        if (!transits.insert(t)) {
            log.info("Transito {} già ricevuto, ignorato", t.eventId());
            return;
        }
        if (Transit.ENTRY.equals(t.type())) {
            trains.arrivedAt(t.trainId(), t.stationId(), t.dir());
        } else {
            trains.departedFrom(t.trainId(), t.stationId(), t.dir(), Instant.parse(t.ts()));
        }
        log.info("Treno {} {} stazione {}", t.trainId(),
                Transit.ENTRY.equals(t.type()) ? "entrato in" : "uscito da", t.stationId());
    }
}
