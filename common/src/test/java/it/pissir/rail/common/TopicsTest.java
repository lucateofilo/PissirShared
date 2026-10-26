package it.pissir.rail.common;

import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicsTest {

    @Test
    void idDaiTopic() {
        assertEquals("B", Topics.stationIdFrom(Topics.stationHeartbeat("B")));
        assertEquals("IN", Topics.sensorIdFrom(Topics.sensorHeartbeat("B", "IN")));
    }

    @Test
    void laCentraleRiceveITransitiDiTutteLeStazioni() {
        assertTrue(MqttTopic.isMatched(Topics.ALL_TRANSITS, Topics.stationTransit("A")));
        assertTrue(MqttTopic.isMatched(Topics.ALL_TRANSITS, Topics.stationTransit("C")));
        assertFalse(MqttTopic.isMatched(Topics.ALL_TRANSITS, Topics.stationHeartbeat("A")));
    }

    @Test
    void laStazioneRiceveSoloISuoiSensori() {
        String filter = Topics.sensorEvent("A", "+");
        assertTrue(MqttTopic.isMatched(filter, Topics.sensorEvent("A", "IN")));
        assertFalse(MqttTopic.isMatched(filter, Topics.sensorEvent("B", "IN")));
    }
}
