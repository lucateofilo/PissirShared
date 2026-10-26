package it.pissir.rail.sensor;

import it.pissir.rail.common.Json;
import it.pissir.rail.common.Mqtt;
import it.pissir.rail.common.Time;
import it.pissir.rail.common.Topics;
import it.pissir.rail.common.msg.Detect;
import it.pissir.rail.common.msg.SensorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Sensore di una stazione (IN = ingresso, OUT = uscita).
 * Quando un treno gli passa davanti, inoltra la rilevazione alla stazione.
 *
 * Uso: java -jar sensor.jar <stazione> <IN|OUT>
 */
public class SensorAgent {

    private static final Logger log = LoggerFactory.getLogger(SensorAgent.class);

    private final String stationId;
    private final String sensorId;
    private final Mqtt mqtt;

    public SensorAgent(String stationId, String sensorId) {
        this.stationId = stationId;
        this.sensorId = sensorId;
        this.mqtt = new Mqtt("sensor-" + stationId + "-" + sensorId);
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Uso: java -jar sensor.jar <stazione> <IN|OUT>");
            System.exit(1);
        }
        new SensorAgent(args[0], args[1]).start();
    }

    public void start() {
        mqtt.connect();
        mqtt.subscribe(Topics.sensorDetect(stationId, sensorId), 1, (topic, payload) -> onDetect(payload));
        log.info("Sensore {} della stazione {} attivo", sensorId, stationId);
    }

    private void onDetect(String payload) {
        Detect detect = Json.parse(payload, Detect.class);
        if (detect.trainId() == null || detect.trainId().isBlank()) {
            log.warn("Rilevazione senza numero di treno, ignorata: {}", payload);
            return;
        }
        // L'eventId permette alla centrale di riconoscere i messaggi ricevuti due volte
        SensorEvent event = new SensorEvent(UUID.randomUUID().toString(), sensorId,
                detect.trainId(), detect.dir(), Time.now());
        mqtt.publish(Topics.sensorEvent(stationId, sensorId), event, 1, false);
        log.info("Rilevato treno {}", detect.trainId());
    }
}
