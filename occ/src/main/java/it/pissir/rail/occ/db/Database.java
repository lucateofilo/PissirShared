package it.pissir.rail.occ.db;

import it.pissir.rail.common.Config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accesso al database con JDBC. Ogni operazione apre e chiude la propria connessione:
 * è la soluzione più semplice e per il carico della demo basta.
 */
public class Database {

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(Config.dbUrl(), Config.dbUser(), Config.dbPassword());
    }

    /** Esegue INSERT, UPDATE o DELETE e restituisce il numero di righe modificate. */
    public int update(String sql, Object... params) {
        try (Connection conn = connect(); PreparedStatement st = prepare(conn, sql, params)) {
            return st.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException(sql, e);
        }
    }

    /** Esegue una SELECT e restituisce ogni riga come mappa colonna -> valore. */
    public List<Map<String, Object>> query(String sql, Object... params) {
        try (Connection conn = connect();
             PreparedStatement st = prepare(conn, sql, params);
             ResultSet rs = st.executeQuery()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (SQLException e) {
            throw new DatabaseException(sql, e);
        }
    }

    private PreparedStatement prepare(Connection conn, String sql, Object... params) throws SQLException {
        PreparedStatement st = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            st.setObject(i + 1, params[i]);
        }
        return st;
    }

    public static class DatabaseException extends RuntimeException {
        public DatabaseException(String sql, SQLException cause) {
            super("Errore SQL in: " + sql, cause);
        }
    }
}
