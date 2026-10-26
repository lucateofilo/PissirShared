package it.pissir.rail.occ.alerting;

import it.pissir.rail.common.Mqtt;
import it.pissir.rail.common.Time;
import it.pissir.rail.common.Topics;
import it.pissir.rail.common.msg.Alert;
import it.pissir.rail.occ.db.AlertDao;
import it.pissir.rail.occ.db.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Allarmi per gli operatori della centrale e avvisi ai treni. */
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final Mqtt mqtt;
    private final AlertDao alerts;

    public AlertService(Mqtt mqtt, Database db) {
        this.mqtt = mqtt;
        this.alerts = new AlertDao(db);
    }

    /** Registra un allarme, che compare nella pagina della centrale. */
    public void raise(String message) {
        log.warn("Allarme: {}", message);
        alerts.insert(message);
    }

    /** Manda un avviso a un treno. */
    public void notifyTrain(String trainId, String message) {
        mqtt.publish(Topics.trainAlert(trainId), new Alert(message, Time.now()), 1, false);
    }
}
