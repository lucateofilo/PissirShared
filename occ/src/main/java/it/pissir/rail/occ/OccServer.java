package it.pissir.rail.occ;

import it.pissir.rail.common.Config;
import it.pissir.rail.common.Mqtt;
import it.pissir.rail.occ.alerting.AlertService;
import it.pissir.rail.occ.alerting.OverdueTrainMonitor;
import it.pissir.rail.occ.api.RestApi;
import it.pissir.rail.occ.db.Database;
import it.pissir.rail.occ.fault.FaultManager;
import it.pissir.rail.occ.ingestion.Ingestion;
import it.pissir.rail.occ.traffic.TrafficLogic;

/**
 * Centrale operativa. Avvia i moduli che ascoltano il broker e il server REST.
 *
 * Uso: java -jar occ.jar
 */
public class OccServer {

    public static void main(String[] args) {
        Database db = new Database();
        Mqtt mqtt = new Mqtt("occ");

        AlertService alerts = new AlertService(mqtt, db);
        TrafficLogic traffic = new TrafficLogic(mqtt, db);
        FaultManager faultManager = new FaultManager(mqtt, db, alerts);

        new Ingestion(mqtt, db, alerts).start();
        traffic.start();
        faultManager.start();
        mqtt.connect();

        new OverdueTrainMonitor(db, alerts).start();
        new RestApi(db, traffic, faultManager).start(Config.httpPort());
    }
}
