package it.pissir.rail.occ.traffic;

import it.pissir.rail.common.Json;
import it.pissir.rail.common.Mqtt;
import it.pissir.rail.common.Topics;
import it.pissir.rail.common.msg.NextStopReply;
import it.pissir.rail.common.msg.NextStopRequest;
import it.pissir.rail.occ.db.Database;
import it.pissir.rail.occ.db.RouteDao;
import it.pissir.rail.occ.db.TrainDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Risponde alle stazioni che chiedono la prossima fermata di un treno. */
public class TrafficLogic {

    private static final Logger log = LoggerFactory.getLogger(TrafficLogic.class);

    private final Mqtt mqtt;
    private final RouteDao routes;
    private final TrainDao trains;

    public TrafficLogic(Mqtt mqtt, Database db) {
        this.mqtt = mqtt;
        this.routes = new RouteDao(db);
        this.trains = new TrainDao(db);
    }

    public void start() {
        mqtt.subscribe(Topics.NEXT_STOP_REQUEST, 1, (topic, payload) -> onRequest(payload));
    }

    private void onRequest(String payload) {
        NextStopRequest req = Json.parse(payload, NextStopRequest.class);
        RouteDao.NextStop stop = routes.findNextStop(req.trainId(), req.dir(), req.stationId());

        NextStopReply reply;
        if (stop == null) {
            // La stazione non è nella tratta del treno: lo trattiamo come capolinea
            log.warn("Stazione {} non presente nella tratta {} del treno {}", req.stationId(), req.dir(), req.trainId());
            reply = new NextStopReply(req.reqId(), req.trainId(), null, 0, 0);
        } else {
            reply = new NextStopReply(req.reqId(), req.trainId(), stop.nextStation(), stop.travelTimeSec(), stop.dwellSec());
            trains.setNextStation(req.trainId(), stop.nextStation());
        }
        mqtt.publish(Topics.stationNextStopReply(req.stationId()), reply, 1, false);
    }
}
