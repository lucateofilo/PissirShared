package it.pissir.rail.common.msg;

/**
 * Comando dalla centrale alla stazione. Topic: station/{S}/cmd
 * DISPATCH: gli operatori arrivano e riparano il guasto in repairSec secondi.
 * SUPPRESS: il treno trainId, fermo in stazione, viene soppresso.
 */
public record StationCommand(String cmd, String trainId, int repairSec) {

    public static StationCommand dispatch(int repairSec) {
        return new StationCommand("DISPATCH", null, repairSec);
    }

    public static StationCommand suppress(String trainId) {
        return new StationCommand("SUPPRESS", trainId, 0);
    }
}
