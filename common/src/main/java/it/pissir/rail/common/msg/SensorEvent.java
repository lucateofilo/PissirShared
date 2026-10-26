package it.pissir.rail.common.msg;

/** Rilevazione inoltrata dal sensore alla stazione. Topic: station/{S}/sensor/{sid}/event */
public record SensorEvent(String eventId, String sensor, String trainId, String dir, String ts) {
}
