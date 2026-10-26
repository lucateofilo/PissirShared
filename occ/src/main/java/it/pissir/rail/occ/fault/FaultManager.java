package it.pissir.rail.occ.fault;

import it.pissir.rail.common.Json;
import it.pissir.rail.common.Mqtt;
import it.pissir.rail.common.Topics;
import it.pissir.rail.common.msg.StationStatus;
import it.pissir.rail.occ.db.Database;
import it.pissir.rail.occ.db.StationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tiene aggiornato nel database lo stato delle stazioni. */
public class FaultManager {

    private static final Logger log = LoggerFactory.getLogger(FaultManager.class);

    private final Mqtt mqtt;
    private final StationDao stations;

    public FaultManager(Mqtt mqtt, Database db) {
        this.mqtt = mqtt;
        this.stations = new StationDao(db);
    }

    public void start() {
        mqtt.subscribe(Topics.ALL_STATION_HEARTBEATS, 0,
                (topic, payload) -> stations.updateHeartbeat(Topics.stationIdFrom(topic)));
        mqtt.subscribe(Topics.ALL_STATION_STATUS, 1, this::onStatus);
    }

    private void onStatus(String topic, String payload) {
        String stationId = Topics.stationIdFrom(topic);
        StationStatus status = Json.parse(payload, StationStatus.class);
        stations.updateState(stationId, status.state());
        log.info("Stazione {}: {}", stationId, status.state());
    }
}
