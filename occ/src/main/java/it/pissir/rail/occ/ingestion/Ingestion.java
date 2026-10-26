package it.pissir.rail.occ.ingestion;

import it.pissir.rail.common.Json;
import it.pissir.rail.common.Mqtt;
import it.pissir.rail.common.Topics;
import it.pissir.rail.common.msg.Transit;
import it.pissir.rail.occ.db.Database;
import it.pissir.rail.occ.db.RouteDao;
import it.pissir.rail.occ.db.TrainDao;
import it.pissir.rail.occ.db.TransitDao;
import it.pissir.rail.occ.traffic.Schedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Riceve i transiti dalle stazioni, calcola il ritardo, li salva e aggiorna la posizione dei treni.
 *
 * L'orario previsto di ogni fermata si calcola a partire dalla partenza effettiva dalla stazione
 * di origine, sommando i tempi della tratta (vedi Schedule). Il ritardo è la differenza tra
 * l'orario reale del transito e quello previsto: è negativo se il treno è in anticipo.
 */
public class Ingestion {

    private static final Logger log = LoggerFactory.getLogger(Ingestion.class);

    private final Mqtt mqtt;
    private final TransitDao transits;
    private final TrainDao trains;
    private final RouteDao routes;

    public Ingestion(Mqtt mqtt, Database db) {
        this.mqtt = mqtt;
        this.transits = new TransitDao(db);
        this.trains = new TrainDao(db);
        this.routes = new RouteDao(db);
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
        Instant ts = Instant.parse(t.ts());
        boolean entry = Transit.ENTRY.equals(t.type());

        Schedule schedule = routes.findSchedule(t.trainId(), t.dir());
        if (schedule != null && !schedule.contains(t.stationId())) {
            schedule = null;
        }
        if (schedule == null) {
            log.warn("Stazione {} non presente nella tratta {} del treno {}: ritardo non calcolato",
                    t.stationId(), t.dir(), t.trainId());
        }

        // L'uscita dalla stazione di origine è l'inizio del viaggio
        boolean journeyStarts = !entry && schedule != null && schedule.isOrigin(t.stationId());
        Instant journeyStart = journeyStarts ? ts : trains.findJourneyStart(t.trainId());

        Instant scheduled = null;
        Integer delay = null;
        if (schedule != null && journeyStart != null) {
            int offset = entry ? schedule.arrivalOffset(t.stationId()) : schedule.departureOffset(t.stationId());
            scheduled = journeyStart.plusSeconds(offset);
            delay = (int) Duration.between(scheduled, ts).toSeconds();
        }

        if (!transits.insert(t, scheduled, delay)) {
            log.info("Transito {} già ricevuto, ignorato", t.eventId());
            return;
        }

        if (entry) {
            trains.arrivedAt(t.trainId(), t.stationId(), t.dir(), delay);
        } else {
            if (journeyStarts) {
                trains.startJourney(t.trainId(), ts);
            }
            // Arrivo stimato alla prossima stazione: ora di partenza più tempo di percorrenza
            Schedule.Stop next = schedule == null ? null : schedule.next(t.stationId());
            Instant expectedArrival = next == null ? null : ts.plusSeconds(next.travelTimeSec());
            trains.departedFrom(t.trainId(), t.stationId(), t.dir(), delay, expectedArrival);
        }
        log.info("Treno {} {} stazione {}, ritardo {} s", t.trainId(),
                entry ? "entrato in" : "uscito da", t.stationId(), delay == null ? "?" : delay);
    }
}
