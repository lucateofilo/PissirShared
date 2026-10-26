package it.pissir.rail.common;

/**
 * Parametri di configurazione letti dalle variabili d'ambiente.
 * Se una variabile non è impostata si usa il valore per lo sviluppo in locale.
 */
public final class Config {

    private Config() {
    }

    public static String mqttUrl() {
        return env("MQTT_URL", "tcp://localhost:1883");
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

    public static int httpPort() {
        return Integer.parseInt(env("HTTP_PORT", "8080"));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
