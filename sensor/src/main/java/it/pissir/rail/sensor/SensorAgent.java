package it.pissir.rail.sensor;

import it.pissir.rail.common.Json;
import it.pissir.rail.common.Mqtt;
import it.pissir.rail.common.Time;
import it.pissir.rail.common.Topics;
import it.pissir.rail.common.msg.Detect;
import it.pissir.rail.common.msg.Heartbeat;
import it.pissir.rail.common.msg.SensorEvent;
import it.pissir.rail.common.msg.SimCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sensore di una stazione (IN = ingresso, OUT = uscita).
 * Quando un treno gli passa davanti, inoltra la rilevazione alla stazione.
 * Ogni 2 secondi manda un heartbeat alla stazione.
 *
 * Uso: java -jar sensor.jar <stazione> <IN|OUT>
 */
public class SensorAgent {

    private static final Logger log = LoggerFactory.getLogger(SensorAgent.class);
    private static final int HEARTBEAT_SECONDS = 2;

    private final String stationId;
    private final String sensorId;
    private final Mqtt mqtt;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Diventa false con il comando di simulazione "kill": il sensore si comporta come se fosse rotto
    private volatile boolean working = true;

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
        mqtt.subscribe(Topics.sensorDetect(stationId, sensorId), 1, (topic, payload) -> onDetect(payload));
        mqtt.subscribe(Topics.sensorSim(stationId, sensorId), 1, (topic, payload) -> onSimCommand(payload));
        mqtt.connect();
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        log.info("Sensore {} della stazione {} attivo", sensorId, stationId);
    }

    private void onDetect(String payload) {
        if (!working) {
            return;
        }
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

    private void onSimCommand(String payload) {
        SimCommand command = Json.parse(payload, SimCommand.class);
        switch (command.cmd()) {
            case "kill" -> working = false;
            case "revive" -> working = true;
            default -> log.warn("Comando di simulazione sconosciuto: {}", command.cmd());
        }
        log.info("Sensore {}", working ? "funzionante" : "guasto (simulato)");
    }

    private void sendHeartbeat() {
        if (working && mqtt.isConnected()) {
            mqtt.publish(Topics.sensorHeartbeat(stationId, sensorId), new Heartbeat(Time.now()), 0, false);
        }
    }
}
