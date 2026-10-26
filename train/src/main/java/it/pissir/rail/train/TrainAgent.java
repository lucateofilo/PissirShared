package it.pissir.rail.train;

import it.pissir.rail.common.Json;
import it.pissir.rail.common.Mqtt;
import it.pissir.rail.common.Time;
import it.pissir.rail.common.Topics;
import it.pissir.rail.common.msg.Alert;
import it.pissir.rail.common.msg.Detect;
import it.pissir.rail.common.msg.SimCommand;
import it.pissir.rail.common.msg.TrainCommand;
import it.pissir.rail.common.msg.TrainReady;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Simulatore di un treno.
 * Parte dalla stazione di origine, esegue gli ordini delle stazioni e si presenta ai loro sensori.
 * Riceve dalla centrale gli avvisi su guasti e ritardi.
 *
 * Uso: java -jar train.jar <numero> <stazione di partenza> <A|R>
 */
public class TrainAgent {

    private static final Logger log = LoggerFactory.getLogger(TrainAgent.class);
    private static final int RESUME_SECONDS = 2;

    private final String trainId;
    private final String dir;
    private final Mqtt mqtt;
    private final ScheduledExecutorService engine = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();

    private volatile String currentStation;
    private volatile String nextStation;
    // Arrivo programmato alla prossima stazione; null quando il treno è fermo in stazione
    private volatile ScheduledFuture<?> arrival;
    // true se il treno si è bloccato tra due stazioni (simulazione)
    private volatile boolean stopped = false;

    public TrainAgent(String trainId, String startStation, String dir) {
        this.trainId = trainId;
        this.currentStation = startStation;
        this.dir = dir;
        this.mqtt = new Mqtt("train-" + trainId);
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Uso: java -jar train.jar <numero> <stazione di partenza> <A|R>");
            System.exit(1);
        }
        new TrainAgent(args[0], args[1], args[2]).start();
    }

    public void start() {
        mqtt.subscribe(Topics.trainCmd(trainId), 1, (topic, payload) -> onCommand(payload));
        mqtt.subscribe(Topics.trainAlert(trainId), 1,
                (topic, payload) -> log.info("Avviso dalla centrale: {}", Json.parse(payload, Alert.class).message()));
        mqtt.subscribe(Topics.trainSim(trainId), 1, (topic, payload) -> onSimCommand(payload));
        mqtt.connect();

        log.info("Treno {} pronto in stazione {}", trainId, currentStation);
        mqtt.publish(Topics.stationReady(currentStation), new TrainReady(trainId, dir, Time.now()), 1, false);
    }

    private void onCommand(String payload) {
        TrainCommand command = Json.parse(payload, TrainCommand.class);
        switch (command.cmd()) {
            case "DEPART" -> depart(command.next(), command.travelTime());
            case "HOLD" -> log.info("Fermo in stazione {}: {}", currentStation, command.reason());
            case "END" -> finish("a fine corsa");
            case "SUPPRESS" -> finish("soppresso");
            default -> log.warn("Comando sconosciuto: {}", command.cmd());
        }
    }

    private void depart(String next, int travelTimeSeconds) {
        if (arrival != null) {
            log.warn("Ordine di partenza ricevuto mentre il treno è già in viaggio, ignorato");
            return;
        }
        detect(currentStation, "OUT");
        nextStation = next;

        // Il tempo reale varia tra il 90% e il 130% di quello previsto (rallentamenti, traffico)
        double factor = 0.9 + random.nextDouble() * 0.4;
        long millis = Math.round(travelTimeSeconds * 1000 * factor);
        log.info("Partito da {} verso {}, arrivo tra {} s", currentStation, nextStation, millis / 1000.0);
        arrival = engine.schedule(this::arrive, millis, TimeUnit.MILLISECONDS);
    }

    private void arrive() {
        arrival = null;
        currentStation = nextStation;
        nextStation = null;
        log.info("Arrivato in stazione {}", currentStation);
        detect(currentStation, "IN");
    }

    /** Simula un treno che si blocca tra due stazioni (stop) e poi riparte (resume). */
    private void onSimCommand(String payload) {
        SimCommand command = Json.parse(payload, SimCommand.class);
        if ("stop".equals(command.cmd()) && arrival != null && !stopped) {
            arrival.cancel(false);
            stopped = true;
            log.info("Treno bloccato tra {} e {} (simulato)", currentStation, nextStation);
        } else if ("resume".equals(command.cmd()) && stopped) {
            stopped = false;
            arrival = engine.schedule(this::arrive, RESUME_SECONDS, TimeUnit.SECONDS);
            log.info("Treno ripartito verso {}", nextStation);
        } else {
            log.warn("Comando di simulazione non applicabile ora: {}", command.cmd());
        }
    }

    /** Il treno si presenta al sensore IN o OUT di una stazione. */
    private void detect(String station, String sensor) {
        mqtt.publish(Topics.sensorDetect(station, sensor), new Detect(trainId, dir, Time.now()), 1, false);
    }

    private void finish(String reason) {
        log.info("Treno {} {} in stazione {}", trainId, reason, currentStation);
        engine.shutdown();
        mqtt.disconnect();
        System.exit(0);
    }
}
