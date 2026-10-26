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
        Schedule schedule = routes.findSchedule(req.trainId(), req.dir());

        NextStopReply reply;
        if (schedule == null || !schedule.contains(req.stationId())) {
            // La stazione non è nella tratta del treno: lo trattiamo come capolinea
            log.warn("Stazione {} non presente nella tratta {} del treno {}", req.stationId(), req.dir(), req.trainId());
            reply = new NextStopReply(req.reqId(), req.trainId(), null, 0, 0);
        } else {
            Schedule.Stop next = schedule.next(req.stationId());
            int dwell = schedule.stop(req.stationId()).dwellSec();
            reply = next == null
                    ? new NextStopReply(req.reqId(), req.trainId(), null, 0, dwell)
                    : new NextStopReply(req.reqId(), req.trainId(), next.stationId(), next.travelTimeSec(), dwell);
            trains.setNextStation(req.trainId(), next == null ? null : next.stationId());
        }
        mqtt.publish(Topics.stationNextStopReply(req.stationId()), reply, 1, false);
    }
}
