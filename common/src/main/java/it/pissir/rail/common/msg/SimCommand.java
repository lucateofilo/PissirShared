package it.pissir.rail.common.msg;

/**
 * Comando usato solo nella demo per simulare guasti (vedi sim.sh).
 * Sensore: kill, revive. Stazione: fault con kind (ad esempio TRACK_BROKEN). Treno: stop, resume.
 */
public record SimCommand(String cmd, String kind) {
}
