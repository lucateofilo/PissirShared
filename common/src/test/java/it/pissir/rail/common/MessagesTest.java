package it.pissir.rail.common;

import it.pissir.rail.common.msg.StationStatus;
import it.pissir.rail.common.msg.TrainCommand;
import it.pissir.rail.common.msg.Transit;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** I messaggi devono arrivare uguali dall'altra parte dopo la conversione in JSON. */
class MessagesTest {

    @Test
    void transitoConvertitoERiletto() {
        Transit sent = new Transit("e1", "B", "101", Transit.ENTRY, "A", "2026-10-26T10:00:20Z");
        String json = new String(Json.toBytes(sent), StandardCharsets.UTF_8);
        assertEquals(sent, Json.parse(json, Transit.class));
    }

    @Test
    void ordineDiPartenza() {
        TrainCommand cmd = Json.parse("{\"cmd\":\"DEPART\",\"next\":\"C\",\"travelTime\":25}", TrainCommand.class);
        assertEquals(TrainCommand.depart("C", 25), cmd);
    }

    @Test
    void campiSconosciutiIgnoratiECampiMancantiNulli() {
        StationStatus status = Json.parse("{\"state\":\"ONLINE\",\"extra\":1}", StationStatus.class);
        assertEquals(StationStatus.ONLINE, status.state());
        assertNull(status.detail());
    }

    @Test
    void jsonNonValido() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("non è json", Transit.class));
    }
}
