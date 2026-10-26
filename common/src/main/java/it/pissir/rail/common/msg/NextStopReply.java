package it.pissir.rail.common.msg;

/**
 * Risposta della centrale. next è null se il treno è arrivato al capolinea.
 * travelTime è il tempo per raggiungere next, dwell la sosta nella stazione attuale (in secondi).
 * Topic: station/{S}/reply/next-stop
 */
public record NextStopReply(String reqId, String trainId, String next, int travelTime, int dwell) {
}
