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
    public static final String ALL_STATION_FAULTS = ROOT + "station/+/fault";

    public static final String NEXT_STOP_REQUEST = ROOT + "occ/request/next-stop";

    private Topics() {
    }

    public static String sensorDetect(String station, String sensor) {
        return sensor(station, sensor) + "/detect";
    }

    public static String sensorEvent(String station, String sensor) {
        return sensor(station, sensor) + "/event";
    }

    public static String sensorHeartbeat(String station, String sensor) {
        return sensor(station, sensor) + "/heartbeat";
    }

    public static String sensorSim(String station, String sensor) {
        return sensor(station, sensor) + "/sim";
    }

    public static String stationTransit(String station) {
        return station(station) + "/transit";
    }

    public static String stationHeartbeat(String station) {
        return station(station) + "/heartbeat";
    }

    public static String stationStatus(String station) {
        return station(station) + "/status";
    }

    public static String stationFault(String station) {
        return station(station) + "/fault";
    }

    public static String stationReady(String station) {
        return station(station) + "/ready";
    }

    public static String stationNextStopReply(String station) {
        return station(station) + "/reply/next-stop";
    }

    public static String stationCmd(String station) {
        return station(station) + "/cmd";
    }

    public static String stationSim(String station) {
        return station(station) + "/sim";
    }

    public static String trainCmd(String train) {
        return train(train) + "/cmd";
    }

    public static String trainAlert(String train) {
        return train(train) + "/alert";
    }

    public static String trainSim(String train) {
        return train(train) + "/sim";
    }

    /** Ricava l'id della stazione da un topic del tipo rail/station/{id}/... */
    public static String stationIdFrom(String topic) {
        return topic.split("/")[2];
    }

    /** Ricava l'id del sensore da un topic del tipo rail/station/{id}/sensor/{sid}/... */
    public static String sensorIdFrom(String topic) {
        return topic.split("/")[4];
    }

    private static String station(String station) {
        return ROOT + "station/" + station;
    }

    private static String sensor(String station, String sensor) {
        return station(station) + "/sensor/" + sensor;
    }

    private static String train(String train) {
        return ROOT + "train/" + train;
    }
}
