package it.pissir.rail.occ.db;

import java.util.List;
import java.util.Map;

public class UserDao {

    /** Utente registrato. role vale TECH (tecnico) oppure ADMIN (amministratore). */
    public record User(String username, String passwordHash, String role) {
    }

    private final Database db;

    public UserDao(Database db) {
        this.db = db;
    }

    public User find(String username) {
        List<Map<String, Object>> rows = db.query(
                "SELECT username, password_hash, role FROM app_user WHERE username = ?", username);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new User((String) row.get("username"), (String) row.get("password_hash"), (String) row.get("role"));
    }
}
