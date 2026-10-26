package it.pissir.rail.occ.alerting;

import it.pissir.rail.occ.db.Database;
import it.pissir.rail.occ.db.TrainDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Controlla ogni 5 secondi i treni in viaggio. Se un treno non è ancora arrivato
 * 15 secondi dopo l'arrivo previsto, è probabilmente fermo tra due stazioni: genera un allarme.
 */
public class OverdueTrainMonitor {

    private static final Logger log = LoggerFactory.getLogger(OverdueTrainMonitor.class);

    private static final int CHECK_SECONDS = 5;
    private static final int TOLERANCE_SECONDS = 15;

    private final TrainDao trains;
    private final AlertService alerts;
    // Treni già segnalati, per non ripetere l'allarme a ogni controllo
    private Set<String> reported = new HashSet<>();

    public OverdueTrainMonitor(Database db, AlertService alerts) {
        this.trains = new TrainDao(db);
        this.alerts = alerts;
    }

    public void start() {
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::check, CHECK_SECONDS, CHECK_SECONDS, TimeUnit.SECONDS);
    }

    private void check() {
        // Un'eccezione non gestita fermerebbe per sempre i controlli periodici
        try {
            findOverdueTrains();
        } catch (RuntimeException e) {
            log.error("Controllo dei treni fermi fallito", e);
        }
    }

    private void findOverdueTrains() {
        Set<String> overdue = new HashSet<>();
        for (Map<String, Object> train : trains.findOverdue(Instant.now().minusSeconds(TOLERANCE_SECONDS))) {
            String id = (String) train.get("id");
            overdue.add(id);
            if (!reported.contains(id)) {
                alerts.raise("Treno " + id + " fermo tra le stazioni " + train.get("last_station")
                        + " e " + train.get("next_station"));
            }
        }
        reported = overdue;
    }
}
