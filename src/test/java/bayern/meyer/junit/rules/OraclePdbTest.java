package bayern.meyer.junit.rules;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.ClassRule;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class OraclePdbTest {
    @ClassRule
    public static OraclePdb oraclePdb = new OraclePdb(new OraclePdb.OraclePdbConfiguration().withCdbPassword("MgP4dODB"));

    @Test
    public void aTest() throws SQLException {
        OracleDataSource pdbAdminDataSource = new OracleDataSource();
        pdbAdminDataSource.setURL(oraclePdb.getPdbJdbcUrl());
        pdbAdminDataSource.setUser(oraclePdb.getPdbAdminUser());
        pdbAdminDataSource.setPassword(oraclePdb.getPdbAdminPassword());
        try (Connection connection = pdbAdminDataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1 FROM DUAL");
        }
    }

}
