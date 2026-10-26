package it.pissir.rail.occ;

import it.pissir.rail.common.Config;
import it.pissir.rail.common.Mqtt;
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
        mqtt.connect();

        new Ingestion(mqtt, db).start();
        new TrafficLogic(mqtt, db).start();
        new FaultManager(mqtt, db).start();
        new RestApi(db).start(Config.httpPort());
    }
}
