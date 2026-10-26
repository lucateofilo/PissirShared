package it.pissir.rail.occ.traffic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleTest {

    // A -> B -> C: 20 s da A a B, sosta di 5 s in B, 25 s da B a C
    private final Schedule schedule = new Schedule(List.of(
            new Schedule.Stop("A", 0, 0),
            new Schedule.Stop("B", 20, 5),
            new Schedule.Stop("C", 25, 0)));

    @Test
    void orariPrevisti() {
        assertEquals(0, schedule.arrivalOffset("A"));
        assertEquals(0, schedule.departureOffset("A"));
        assertEquals(20, schedule.arrivalOffset("B"));
        assertEquals(25, schedule.departureOffset("B"));
        assertEquals(50, schedule.arrivalOffset("C"));
    }

    @Test
    void fermataSuccessiva() {
        assertEquals("B", schedule.next("A").stationId());
        assertEquals("C", schedule.next("B").stationId());
        assertNull(schedule.next("C"));
    }

    @Test
    void origineEStazioniDellaTratta() {
        assertTrue(schedule.isOrigin("A"));
        assertFalse(schedule.isOrigin("B"));
        assertFalse(schedule.contains("D"));
        assertThrows(IllegalArgumentException.class, () -> schedule.arrivalOffset("D"));
    }
}
