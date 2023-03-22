package code.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
public class SqliteUtil {

    public interface SqliteInterface {
        Object execute(Statement statement) throws SQLException;
    }

    public static Object execute(String dbPath, SqliteInterface sqliteInterface) throws Exception {
        Connection connection = null;
        try {
            File file = new File(dbPath);
            boolean exists = file.exists();
            if (!exists) {
                file.createNewFile();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);
            return sqliteInterface.execute(statement);
        } catch (Exception e) {
            throw e;
        } finally {
            if (null != connection) {
                connection.close();
            }
        }
    }

}
