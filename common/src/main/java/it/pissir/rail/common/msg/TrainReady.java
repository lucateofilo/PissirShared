package it.pissir.rail.common.msg;

/** Il treno è pronto a partire dalla stazione di origine. Topic: station/{S}/ready */
public record TrainReady(String trainId, String dir, String ts) {
}
