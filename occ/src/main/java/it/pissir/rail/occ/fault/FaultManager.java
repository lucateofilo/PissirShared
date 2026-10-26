package it.pissir.rail.occ.fault;

import it.pissir.rail.common.Json;
import it.pissir.rail.common.Mqtt;
import it.pissir.rail.common.Topics;
import it.pissir.rail.common.msg.FaultMessage;
import it.pissir.rail.common.msg.StationCommand;
import it.pissir.rail.common.msg.StationStatus;
import it.pissir.rail.occ.alerting.AlertService;
import it.pissir.rail.occ.db.Database;
import it.pissir.rail.occ.db.FaultDao;
import it.pissir.rail.occ.db.StationDao;
import it.pissir.rail.occ.db.TrainDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tiene sotto controllo lo stato delle stazioni e gestisce i guasti permanenti.
 *
 * Una stazione è considerata non raggiungibile in due casi: il broker pubblica il suo
 * Last Will (connessione caduta) oppure non arrivano heartbeat da 15 secondi (processo bloccato).
 */
public class FaultManager {

    private static final Logger log = LoggerFactory.getLogger(FaultManager.class);
    private static final int CHECK_SECONDS = 5;
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 15;
    // Tempo simulato che gli operatori impiegano a riparare un guasto
    private static final int REPAIR_SECONDS = 20;

    private final Mqtt mqtt;
    private final StationDao stations;
    private final FaultDao faults;
    private final TrainDao trains;
    private final AlertService alerts;

    // Ultimo stato noto e ultimo heartbeat di ogni stazione
    private final Map<String, String> states = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastHeartbeat = new ConcurrentHashMap<>();

    public FaultManager(Mqtt mqtt, Database db, AlertService alerts) {
        this.mqtt = mqtt;
        this.stations = new StationDao(db);
        this.faults = new FaultDao(db);
        this.trains = new TrainDao(db);
        this.alerts = alerts;
    }

    public void start() {
        // Finché una stazione non si fa sentire la consideriamo non raggiungibile
        stations.markAllOffline();
        stations.findAll().forEach(row -> states.put((String) row.get("id"), StationStatus.OFFLINE));
        mqtt.subscribe(Topics.ALL_STATION_HEARTBEATS, 0, this::onHeartbeat);
        mqtt.subscribe(Topics.ALL_STATION_STATUS, 1, this::onStatus);
        mqtt.subscribe(Topics.ALL_STATION_FAULTS, 1, this::onFault);
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::checkHeartbeats, CHECK_SECONDS, CHECK_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Il tecnico ha inviato gli operatori a riparare un guasto.
     * Restituisce false se il guasto non esiste o non è più in stato OPEN.
     */
    public boolean dispatch(int faultId, String username) {
        String stationId = faults.findOpenStation(faultId);
        if (stationId == null) {
            return false;
        }
        faults.markDispatched(faultId, username);
        mqtt.publish(Topics.stationCmd(stationId), StationCommand.dispatch(REPAIR_SECONDS), 1, false);
        log.info("Operatori inviati alla stazione {} da {}", stationId, username);
        return true;
    }

    private void onHeartbeat(String topic, String payload) {
        String stationId = Topics.stationIdFrom(topic);
        StationStatus status = Json.parse(payload, StationStatus.class);
        lastHeartbeat.put(stationId, Instant.now());
        stations.updateHeartbeat(stationId);
        changeState(stationId, status.state(), status.detail());
    }

    private void onStatus(String topic, String payload) {
        StationStatus status = Json.parse(payload, StationStatus.class);
        changeState(Topics.stationIdFrom(topic), status.state(), status.detail());
    }

    private void changeState(String stationId, String state, String detail) {
        String previous = states.put(stationId, state);
        stations.updateState(stationId, state, detail);
        if (state.equals(previous)) {
            return;
        }
        log.info("Stazione {}: {}", stationId, state);
        if (StationStatus.OFFLINE.equals(state)) {
            alerts.raise("Stazione " + stationId + " non raggiungibile (" + detail + ")");
        } else if (StationStatus.DEGRADED.equals(state)) {
            alerts.raise("Stazione " + stationId + ": " + detail);
        }
    }

    private void checkHeartbeats() {
        Instant limit = Instant.now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);
        lastHeartbeat.forEach((stationId, last) -> {
            if (last.isBefore(limit) && !StationStatus.OFFLINE.equals(states.get(stationId))) {
                try {
                    changeState(stationId, StationStatus.OFFLINE,
                            "nessun heartbeat da " + HEARTBEAT_TIMEOUT_SECONDS + " s");
                } catch (RuntimeException e) {
                    // Un'eccezione non gestita fermerebbe per sempre i controlli periodici
                    log.error("Controllo heartbeat della stazione {} fallito", stationId, e);
                }
            }
        });
    }

    private void onFault(String topic, String payload) {
        String stationId = Topics.stationIdFrom(topic);
        FaultMessage fault = Json.parse(payload, FaultMessage.class);
        if (FaultMessage.OPEN.equals(fault.action())) {
            faults.open(stationId, fault.kind());
            alerts.raise("Guasto nella stazione " + stationId + ": " + fault.kind());
            for (Map<String, Object> train : trains.findArrivingAt(stationId)) {
                alerts.notifyTrain((String) train.get("id"),
                        "Guasto nella stazione " + stationId + ", il treno verrà fermato all'arrivo");
            }
        } else {
            faults.resolve(stationId);
            alerts.raise("Stazione " + stationId + " riparata");
        }
    }
}
