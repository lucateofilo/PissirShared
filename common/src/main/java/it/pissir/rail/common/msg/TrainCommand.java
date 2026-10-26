package it.pissir.rail.common.msg;

/** Ordine dalla stazione al treno: DEPART (riparti verso next) oppure END (fine corsa). Topic: train/{id}/cmd */
public record TrainCommand(String cmd, String next, int travelTime) {

    public static TrainCommand depart(String next, int travelTime) {
        return new TrainCommand("DEPART", next, travelTime);
    }

    public static TrainCommand end() {
        return new TrainCommand("END", null, 0);
    }
}
