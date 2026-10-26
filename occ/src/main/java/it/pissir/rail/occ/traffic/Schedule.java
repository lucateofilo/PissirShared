package it.pissir.rail.occ.traffic;

import java.util.List;

/**
 * Fermate di una tratta, in ordine, con i tempi previsti.
 *
 * Gli orari previsti sono espressi come secondi dalla partenza dalla stazione di origine.
 * Esempio con fermate A (sosta 0), B (viaggio 20 s, sosta 5 s), C (viaggio 25 s):
 * arrivo previsto in B dopo 20 s, partenza da B dopo 25 s, arrivo in C dopo 50 s.
 */
public class Schedule {

    /** travelTimeSec è il tempo dalla fermata precedente, dwellSec la sosta in questa fermata. */
    public record Stop(String stationId, int travelTimeSec, int dwellSec) {
    }

    private final List<Stop> stops;

    public Schedule(List<Stop> stops) {
        this.stops = List.copyOf(stops);
    }

    public boolean contains(String stationId) {
        return indexOf(stationId) >= 0;
    }

    public boolean isOrigin(String stationId) {
        return indexOf(stationId) == 0;
    }

    public Stop stop(String stationId) {
        return stops.get(requireIndex(stationId));
    }

    /** Fermata successiva, oppure null se stationId è il capolinea. */
    public Stop next(String stationId) {
        int i = requireIndex(stationId);
        return i + 1 < stops.size() ? stops.get(i + 1) : null;
    }

    /** Secondi tra la partenza dall'origine e l'arrivo previsto in stationId. */
    public int arrivalOffset(String stationId) {
        int target = requireIndex(stationId);
        int seconds = 0;
        for (int i = 1; i <= target; i++) {
            seconds += stops.get(i - 1).dwellSec() + stops.get(i).travelTimeSec();
        }
        return seconds;
    }

    /** Secondi tra la partenza dall'origine e la partenza prevista da stationId. */
    public int departureOffset(String stationId) {
        return arrivalOffset(stationId) + stop(stationId).dwellSec();
    }

    private int indexOf(String stationId) {
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).stationId().equals(stationId)) {
                return i;
            }
        }
        return -1;
    }

    private int requireIndex(String stationId) {
        int i = indexOf(stationId);
        if (i < 0) {
            throw new IllegalArgumentException("La stazione " + stationId + " non fa parte della tratta");
        }
        return i;
    }
}
