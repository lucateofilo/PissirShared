package it.pissir.rail.station;

import it.pissir.rail.common.Json;
import it.pissir.rail.common.Mqtt;
import it.pissir.rail.common.Time;
import it.pissir.rail.common.Topics;
import it.pissir.rail.common.msg.FaultMessage;
import it.pissir.rail.common.msg.NextStopReply;
import it.pissir.rail.common.msg.NextStopRequest;
import it.pissir.rail.common.msg.SensorEvent;
import it.pissir.rail.common.msg.SimCommand;
import it.pissir.rail.common.msg.StationCommand;
import it.pissir.rail.common.msg.StationStatus;
import it.pissir.rail.common.msg.TrainCommand;
import it.pissir.rail.common.msg.TrainReady;
import it.pissir.rail.common.msg.Transit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Nodo stazione. Riceve le rilevazioni dei sensori, le comunica alla centrale
 * e dice ai treni quando e dove ripartire.
 *
 * Controlla anche i propri sensori tramite heartbeat e gestisce i guasti permanenti:
 * durante un guasto i treni in stazione restano fermi finché gli operatori non lo riparano.
 *
 * Uso: java -jar station.jar <stazione>
 */
public class StationNode {

    private static final Logger log = LoggerFactory.getLogger(StationNode.class);
    private static final int HEARTBEAT_SECONDS = 5;
    private static final int WATCHDOG_SECONDS = 2;
    // Un sensore è considerato guasto dopo 3 heartbeat mancati (uno ogni 2 secondi)
    private static final long SENSOR_TIMEOUT_MILLIS = 6000;
    private static final List<String> SENSORS = List.of("IN", "OUT");

    private final String id;
    private final Mqtt mqtt;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Ultimo heartbeat ricevuto da ogni sensore (millisecondi)
    private final Map<String, Long> lastSensorHeartbeat = new ConcurrentHashMap<>();
    // Partenze programmate dei treni in sosta: numero del treno -> partenza
    private final Map<String, ScheduledFuture<?>> departures = new ConcurrentHashMap<>();
    // Treni fermi a causa di un guasto: numero del treno -> ordine di partenza da inviare dopo la riparazione
    private final Map<String, TrainCommand> heldTrains = new ConcurrentHashMap<>();

    private volatile Set<String> downSensors = Set.of();
    // Tipo del guasto permanente in corso, null se non ci sono guasti
    private volatile String faultKind;
    private volatile boolean repairing = false;

    public StationNode(String id) {
        this.id = id;
        this.mqtt = new Mqtt("station-" + id);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: java -jar station.jar <stazione>");
            System.exit(1);
        }
        new StationNode(args[0]).start();
    }

    public void start() {
        // Se la stazione cade, il broker avvisa la centrale pubblicando OFFLINE al posto suo
        mqtt.setLastWill(Topics.stationStatus(id), new StationStatus(StationStatus.OFFLINE, "Connessione persa", null));

        mqtt.subscribe(Topics.sensorEvent(id, "+"), 1, (topic, payload) -> onSensorEvent(payload));
        mqtt.subscribe(Topics.sensorHeartbeat(id, "+"), 0,
                (topic, payload) -> lastSensorHeartbeat.put(Topics.sensorIdFrom(topic), System.currentTimeMillis()));
        mqtt.subscribe(Topics.stationReady(id), 1, (topic, payload) -> onTrainReady(payload));
        mqtt.subscribe(Topics.stationNextStopReply(id), 1, (topic, payload) -> onNextStopReply(payload));
        mqtt.subscribe(Topics.stationCmd(id), 1, (topic, payload) -> onCommand(payload));
        mqtt.subscribe(Topics.stationSim(id), 1, (topic, payload) -> onSimCommand(payload));
        mqtt.connect();

        // All'avvio diamo ai sensori il tempo di farsi sentire
        long now = System.currentTimeMillis();
        SENSORS.forEach(sensor -> lastSensorHeartbeat.put(sensor, now));

        publishStatus();
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkSensors, WATCHDOG_SECONDS, WATCHDOG_SECONDS, TimeUnit.SECONDS);
        log.info("Stazione {} attiva", id);
    }

    // Treni

    private void onSensorEvent(String payload) {
        SensorEvent event = Json.parse(payload, SensorEvent.class);
        if ("IN".equals(event.sensor())) {
            sendTransit(event, Transit.ENTRY);
            askNextStop(event.trainId(), event.dir());
        } else if ("OUT".equals(event.sensor())) {
            sendTransit(event, Transit.EXIT);
        } else {
            log.warn("Sensore sconosciuto: {}", event.sensor());
        }
    }

    /** Un treno è pronto a partire da qui, che è la sua stazione di origine. */
    private void onTrainReady(String payload) {
        TrainReady ready = Json.parse(payload, TrainReady.class);
        askNextStop(ready.trainId(), ready.dir());
    }

    private void askNextStop(String trainId, String dir) {
        NextStopRequest request = new NextStopRequest(UUID.randomUUID().toString(), id, trainId, dir);
        mqtt.publish(Topics.NEXT_STOP_REQUEST, request, 1, false);
    }

    private void onNextStopReply(String payload) {
        NextStopReply reply = Json.parse(payload, NextStopReply.class);
        if (reply.next() == null) {
            log.info("Treno {} arrivato al capolinea", reply.trainId());
            mqtt.publish(Topics.trainCmd(reply.trainId()), TrainCommand.end(), 1, false);
            return;
        }
        // Il treno resta in stazione per il tempo di sosta, poi riparte (se non ci sono guasti)
        TrainCommand depart = TrainCommand.depart(reply.next(), reply.travelTime());
        departures.put(reply.trainId(),
                scheduler.schedule(() -> departOrHold(reply.trainId(), depart), reply.dwell(), TimeUnit.SECONDS));
        log.info("Treno {} riparte tra {} s verso {}", reply.trainId(), reply.dwell(), reply.next());
    }

    private void departOrHold(String trainId, TrainCommand depart) {
        departures.remove(trainId);
        if (faultKind != null) {
            heldTrains.put(trainId, depart);
            mqtt.publish(Topics.trainCmd(trainId), TrainCommand.hold("Guasto in stazione " + id + ": " + faultKind), 1, false);
            log.info("Treno {} fermo per il guasto", trainId);
        } else {
            mqtt.publish(Topics.trainCmd(trainId), depart, 1, false);
        }
    }

    private void sendTransit(SensorEvent event, String type) {
        Transit transit = new Transit(event.eventId(), id, event.trainId(), type, event.dir(), event.ts());
        mqtt.publish(Topics.stationTransit(id), transit, 1, false);
    }

    // Comandi della centrale

    private void onCommand(String payload) {
        StationCommand command = Json.parse(payload, StationCommand.class);
        switch (command.cmd()) {
            case "DISPATCH" -> startRepair(command.repairSec());
            case "SUPPRESS" -> suppress(command.trainId());
            default -> log.warn("Comando sconosciuto: {}", command.cmd());
        }
    }

    private void startRepair(int repairSec) {
        if (faultKind == null || repairing) {
            log.warn("Operatori inviati ma non c'è un guasto da riparare");
            return;
        }
        repairing = true;
        publishStatus();
        log.info("Operatori al lavoro, riparazione tra {} s", repairSec);
        scheduler.schedule(this::endRepair, repairSec, TimeUnit.SECONDS);
    }

    private void endRepair() {
        String kind = faultKind;
        faultKind = null;
        repairing = false;
        mqtt.publish(Topics.stationFault(id), new FaultMessage(kind, FaultMessage.CLOSE, Time.now()), 1, false);
        publishStatus();
        log.info("Guasto riparato, ripartono {} treni", heldTrains.size());
        heldTrains.forEach((trainId, depart) -> mqtt.publish(Topics.trainCmd(trainId), depart, 1, false));
        heldTrains.clear();
    }

    private void suppress(String trainId) {
        ScheduledFuture<?> departure = departures.remove(trainId);
        if (departure != null) {
            departure.cancel(false);
        }
        heldTrains.remove(trainId);
        mqtt.publish(Topics.trainCmd(trainId), TrainCommand.suppress(), 1, false);
        log.info("Treno {} soppresso", trainId);
    }

    // Guasti simulati

    private void onSimCommand(String payload) {
        SimCommand command = Json.parse(payload, SimCommand.class);
        if (!"fault".equals(command.cmd()) || command.kind() == null) {
            log.warn("Comando di simulazione sconosciuto: {}", payload);
            return;
        }
        if (faultKind != null) {
            log.warn("C'è già un guasto in corso");
            return;
        }
        faultKind = command.kind();
        mqtt.publish(Topics.stationFault(id), new FaultMessage(faultKind, FaultMessage.OPEN, Time.now()), 1, false);
        publishStatus();
        log.info("Guasto permanente: {}", faultKind);
    }

    // Stato della stazione

    private void checkSensors() {
        long now = System.currentTimeMillis();
        Set<String> down = new TreeSet<>();
        for (String sensor : SENSORS) {
            if (now - lastSensorHeartbeat.get(sensor) > SENSOR_TIMEOUT_MILLIS) {
                down.add(sensor);
            }
        }
        if (!down.equals(downSensors)) {
            downSensors = down;
            log.info(down.isEmpty() ? "Sensori di nuovo attivi" : "Sensori non raggiungibili: {}", down);
            publishStatus();
        }
    }

    private StationStatus currentStatus() {
        if (repairing) {
            return new StationStatus(StationStatus.REPAIRING, "Riparazione in corso: " + faultKind, Time.now());
        }
        if (faultKind != null) {
            return new StationStatus(StationStatus.FAULT, "Guasto: " + faultKind, Time.now());
        }
        if (!downSensors.isEmpty()) {
            return new StationStatus(StationStatus.DEGRADED, "Sensori non raggiungibili: " + String.join(", ", downSensors), Time.now());
        }
        return new StationStatus(StationStatus.ONLINE, null, Time.now());
    }

    private void publishStatus() {
        mqtt.publish(Topics.stationStatus(id), currentStatus(), 1, true);
    }

    private void sendHeartbeat() {
        if (mqtt.isConnected()) {
            mqtt.publish(Topics.stationHeartbeat(id), currentStatus(), 0, false);
        }
    }
}
