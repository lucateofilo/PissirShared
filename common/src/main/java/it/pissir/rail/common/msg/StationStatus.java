package it.pissir.rail.common.msg;

/**
 * Stato della stazione: ONLINE, DEGRADED (sensore guasto), FAULT (guasto permanente),
 * REPAIRING (operatori al lavoro) oppure OFFLINE. detail spiega il motivo, se c'è.
 * Viene pubblicato su station/{S}/status quando cambia e su station/{S}/heartbeat ogni 5 secondi.
 */
public record StationStatus(String state, String detail, String ts) {

    public static final String ONLINE = "ONLINE";
    public static final String DEGRADED = "DEGRADED";
    public static final String FAULT = "FAULT";
    public static final String REPAIRING = "REPAIRING";
    public static final String OFFLINE = "OFFLINE";
}
