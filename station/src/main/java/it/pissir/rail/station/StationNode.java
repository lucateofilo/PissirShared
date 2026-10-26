package it.pissir.rail.station;

import it.pissir.rail.common.Json;
import it.pissir.rail.common.Mqtt;
import it.pissir.rail.common.Time;
import it.pissir.rail.common.Topics;
import it.pissir.rail.common.msg.Heartbeat;
import it.pissir.rail.common.msg.NextStopReply;
import it.pissir.rail.common.msg.NextStopRequest;
import it.pissir.rail.common.msg.SensorEvent;
import it.pissir.rail.common.msg.StationStatus;
import it.pissir.rail.common.msg.TrainCommand;
import it.pissir.rail.common.msg.TrainReady;
import it.pissir.rail.common.msg.Transit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Nodo stazione. Riceve le rilevazioni dei sensori, le comunica alla centrale
 * e dice ai treni quando e dove ripartire.
 *
 * Uso: java -jar station.jar <stazione>
 */
public class StationNode {

    private static final Logger log = LoggerFactory.getLogger(StationNode.class);
    private static final int HEARTBEAT_SECONDS = 5;

    private final String id;
    private final Mqtt mqtt;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

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
        mqtt.setLastWill(Topics.stationStatus(id), new StationStatus("OFFLINE", null));
        mqtt.connect();

        mqtt.subscribe(Topics.sensorEvent(id, "+"), 1, (topic, payload) -> onSensorEvent(payload));
        mqtt.subscribe(Topics.stationReady(id), 1, (topic, payload) -> onTrainReady(payload));
        mqtt.subscribe(Topics.stationNextStopReply(id), 1, (topic, payload) -> onNextStopReply(payload));

        mqtt.publish(Topics.stationStatus(id), new StationStatus("ONLINE", Time.now()), 1, true);
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        log.info("Stazione {} attiva", id);
    }

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
        String trainTopic = Topics.trainCmd(reply.trainId());

        if (reply.next() == null) {
            log.info("Treno {} arrivato al capolinea", reply.trainId());
            mqtt.publish(trainTopic, TrainCommand.end(), 1, false);
            return;
        }
        log.info("Treno {} riparte tra {} s verso {}", reply.trainId(), reply.dwell(), reply.next());
        // Il treno resta in stazione per il tempo di sosta, poi riceve l'ordine di partenza
        scheduler.schedule(
                () -> mqtt.publish(trainTopic, TrainCommand.depart(reply.next(), reply.travelTime()), 1, false),
                reply.dwell(), TimeUnit.SECONDS);
    }

    private void sendTransit(SensorEvent event, String type) {
        Transit transit = new Transit(event.eventId(), id, event.trainId(), type, event.dir(), event.ts());
        mqtt.publish(Topics.stationTransit(id), transit, 1, false);
    }

    private void sendHeartbeat() {
        if (mqtt.isConnected()) {
            mqtt.publish(Topics.stationHeartbeat(id), new Heartbeat(Time.now()), 0, false);
        }
    }
}
