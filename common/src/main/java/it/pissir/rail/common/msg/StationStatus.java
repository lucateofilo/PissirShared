package it.pissir.rail.common.msg;

/** Stato della stazione, ad esempio ONLINE oppure OFFLINE. Topic: station/{S}/status */
public record StationStatus(String state, String ts) {
}
