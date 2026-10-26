package app.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Db {
    public static final String URL = "jdbc:h2:file:./data/guest;AUTO_SERVER=TRUE";

    private Db() {
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, "sa", "");
    }
}
