package it.pissir.rail.common;

/**
 * Nomi dei topic MQTT, raccolti in un unico punto.
 * L'elenco completo con i formati dei messaggi è in docs/architettura.md, sezione 4.
 */
public final class Topics {

    private static final String ROOT = "rail/";

    // Topic con wildcard usati dalla centrale per ascoltare tutte le stazioni
    public static final String ALL_TRANSITS = ROOT + "station/+/transit";
    public static final String ALL_STATION_HEARTBEATS = ROOT + "station/+/heartbeat";
    public static final String ALL_STATION_STATUS = ROOT + "station/+/status";

    public static final String NEXT_STOP_REQUEST = ROOT + "occ/request/next-stop";

    private Topics() {
    }

    public static String sensorDetect(String station, String sensor) {
        return ROOT + "station/" + station + "/sensor/" + sensor + "/detect";
    }

    public static String sensorEvent(String station, String sensor) {
        return ROOT + "station/" + station + "/sensor/" + sensor + "/event";
    }

    public static String stationTransit(String station) {
        return ROOT + "station/" + station + "/transit";
    }

    public static String stationHeartbeat(String station) {
        return ROOT + "station/" + station + "/heartbeat";
    }

    public static String stationStatus(String station) {
        return ROOT + "station/" + station + "/status";
    }

    public static String stationReady(String station) {
        return ROOT + "station/" + station + "/ready";
    }

    public static String stationNextStopReply(String station) {
        return ROOT + "station/" + station + "/reply/next-stop";
    }

    public static String trainCmd(String train) {
        return ROOT + "train/" + train + "/cmd";
    }

    /** Ricava l'id della stazione da un topic del tipo rail/station/{id}/... */
    public static String stationIdFrom(String topic) {
        return topic.split("/")[2];
    }
}
