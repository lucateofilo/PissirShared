package it.pissir.rail.common.msg;

/** Apertura (OPEN) o chiusura (CLOSE) di un guasto permanente. Topic: station/{S}/fault */
public record FaultMessage(String kind, String action, String ts) {

    public static final String OPEN = "OPEN";
    public static final String CLOSE = "CLOSE";
}
