package it.pissir.rail.common.msg;

/** Ingresso (ENTRY) o uscita (EXIT) di un treno. Topic: station/{S}/transit */
public record Transit(String eventId, String stationId, String trainId, String type, String dir, String ts) {

    public static final String ENTRY = "ENTRY";
    public static final String EXIT = "EXIT";
}
