package it.pissir.rail.common;

/**
 * Parametri di configurazione letti dalle variabili d'ambiente.
 * Se una variabile non è impostata si usa il valore per la demo in locale;
 * utente e password MQTT invece sono obbligatori (li imposta launch.sh).
 */
public final class Config {

    private Config() {
    }

    public static String mqttUrl() {
        return env("MQTT_URL", "ssl://localhost:8883");
    }

    public static String mqttUser() {
        return required("MQTT_USER");
    }

    public static String mqttPassword() {
        return required("MQTT_PASSWORD");
    }

    /** Certificato della CA del progetto, usato per verificare quello del broker. */
    public static String caFile() {
        return env("CA_FILE", "infra/certs/ca.crt");
    }

    /** Certificato e chiave con cui la centrale offre HTTPS. */
    public static String serverCertFile() {
        return env("SERVER_CERT_FILE", "infra/certs/server.crt");
    }

    public static String serverKeyFile() {
        return env("SERVER_KEY_FILE", "infra/certs/server.key");
    }

    public static String dbUrl() {
        return env("DB_URL", "jdbc:postgresql://localhost:5432/rail");
    }

    public static String dbUser() {
        return env("DB_USER", "rail");
    }

    public static String dbPassword() {
        return env("DB_PASSWORD", "rail");
    }

    public static int httpsPort() {
        return Integer.parseInt(env("HTTPS_PORT", "8443"));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variabile d'ambiente " + name + " non impostata");
        }
        return value;
    }
}
