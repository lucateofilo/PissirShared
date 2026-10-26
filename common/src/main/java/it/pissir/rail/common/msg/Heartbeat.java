package it.pissir.rail.common.msg;

/** Heartbeat di un sensore verso la sua stazione. Topic: station/{S}/sensor/{sid}/heartbeat */
public record Heartbeat(String ts) {
}
