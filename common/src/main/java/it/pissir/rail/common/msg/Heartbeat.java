package it.pissir.rail.common.msg;

/** Heartbeat della stazione verso la centrale. Topic: station/{S}/heartbeat */
public record Heartbeat(String ts) {
}
