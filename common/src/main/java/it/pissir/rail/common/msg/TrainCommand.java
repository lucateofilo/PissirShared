package it.pissir.rail.common.msg;

/**
 * Ordine dalla stazione al treno. Topic: train/{id}/cmd
 * DEPART: riparti verso next. HOLD: resta fermo (reason spiega perché).
 * END: fine corsa. SUPPRESS: il treno è stato soppresso.
 */
public record TrainCommand(String cmd, String next, int travelTime, String reason) {

    public static TrainCommand depart(String next, int travelTime) {
        return new TrainCommand("DEPART", next, travelTime, null);
    }

    public static TrainCommand hold(String reason) {
        return new TrainCommand("HOLD", null, 0, reason);
    }

    public static TrainCommand end() {
        return new TrainCommand("END", null, 0, null);
    }

    public static TrainCommand suppress() {
        return new TrainCommand("SUPPRESS", null, 0, null);
    }
}
