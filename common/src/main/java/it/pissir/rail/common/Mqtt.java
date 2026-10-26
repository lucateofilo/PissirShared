package it.pissir.rail.common;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Client MQTT usato da tutti i servizi.
 *
 * Rispetto al client Paho aggiunge tre cose:
 * - i messaggi sono oggetti Java convertiti in JSON;
 * - dopo una riconnessione rinnova da solo le sottoscrizioni;
 * - i messaggi ricevuti vengono gestiti su un thread separato, così la callback
 *   di Paho non resta bloccata mentre il servizio pubblica o interroga il database.
 */
public class Mqtt implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(Mqtt.class);

    private record Subscription(int qos, BiConsumer<String, String> handler) {
    }

    private final MqttClient client;
    private final MqttConnectOptions options = new MqttConnectOptions();
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    // Un solo thread: i messaggi vengono gestiti nell'ordine di arrivo
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public Mqtt(String clientId) {
        try {
            client = new MqttClient(Config.mqttUrl(), clientId, new MemoryPersistence());
        } catch (MqttException e) {
            throw new IllegalStateException("Impossibile creare il client MQTT", e);
        }
        client.setCallback(this);

        options.setAutomaticReconnect(true);
        // Sessione persistente: il broker conserva i messaggi QoS 1 mentre siamo disconnessi
        options.setCleanSession(false);
        options.setConnectionTimeout(5);
        options.setKeepAliveInterval(10);
    }

    /** Messaggio che il broker pubblica al posto nostro se cadiamo. Va chiamato prima di connect(). */
    public void setLastWill(String topic, Object message) {
        options.setWill(topic, Json.toBytes(message), 1, true);
    }

    /** Si connette al broker, riprovando ogni 2 secondi finché non ci riesce. */
    public void connect() {
        while (true) {
            try {
                client.connect(options);
                log.info("Connesso a {} come {}", Config.mqttUrl(), client.getClientId());
                return;
            } catch (MqttException e) {
                log.warn("Broker non raggiungibile ({}), riprovo tra 2 secondi", e.getMessage());
                sleep(2000);
            }
        }
    }

    public void subscribe(String topicFilter, int qos, BiConsumer<String, String> handler) {
        subscriptions.put(topicFilter, new Subscription(qos, handler));
        if (client.isConnected()) {
            try {
                client.subscribe(topicFilter, qos);
            } catch (MqttException e) {
                log.error("Sottoscrizione a {} fallita", topicFilter, e);
            }
        }
    }

    /** Pubblica un messaggio. Con QoS 1 il metodo ritorna quando il broker ha confermato la ricezione. */
    public void publish(String topic, Object message, int qos, boolean retained) {
        try {
            client.publish(topic, Json.toBytes(message), qos, retained);
        } catch (MqttException e) {
            log.error("Pubblicazione su {} fallita: {}", topic, e.getMessage());
        }
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    public void disconnect() {
        try {
            client.disconnect();
        } catch (MqttException e) {
            log.warn("Errore in disconnessione: {}", e.getMessage());
        }
        worker.shutdown();
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect) {
            log.info("Riconnesso al broker");
        }
        // Rinnoviamo le sottoscrizioni su un altro thread: qui siamo dentro la callback di Paho
        worker.submit(() -> subscriptions.forEach((filter, sub) -> {
            try {
                client.subscribe(filter, sub.qos());
            } catch (MqttException e) {
                log.error("Sottoscrizione a {} fallita", filter, e);
            }
        }));
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Connessione al broker persa: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = Json.toString(message.getPayload());
        subscriptions.forEach((filter, sub) -> {
            if (MqttTopic.isMatched(filter, topic)) {
                worker.submit(() -> {
                    try {
                        sub.handler().accept(topic, payload);
                    } catch (RuntimeException e) {
                        log.error("Errore nella gestione del messaggio su {}: {}", topic, payload, e);
                    }
                });
            }
        });
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Vuoto: publish() aspetta già la conferma del broker
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
