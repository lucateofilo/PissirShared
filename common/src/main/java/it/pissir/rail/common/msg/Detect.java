package it.pissir.rail.common.msg;

/** Il treno passa davanti a un sensore. Topic: station/{S}/sensor/{sid}/detect */
public record Detect(String trainId, String dir, String ts) {
}
