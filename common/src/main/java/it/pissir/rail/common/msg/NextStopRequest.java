package it.pissir.rail.common.msg;

/** La stazione chiede alla centrale la prossima fermata di un treno. Topic: occ/request/next-stop */
public record NextStopRequest(String reqId, String stationId, String trainId, String dir) {
}
