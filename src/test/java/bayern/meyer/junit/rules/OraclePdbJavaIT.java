package bayern.meyer.junit.rules;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class OraclePdbJavaIT {

    @ClassRule
    public static OraclePdb oraclePdbWithoutConfiguration = new OraclePdb();

    @ClassRule
    public static OraclePdb oraclePdbWithConfiguration = new OraclePdb(new OraclePdbConfiguration.Builder().grantUnlimitedTablespace().andCreatePdbEager().build());

    @BeforeClass
    public static void checkIfPdbIsCreatedEager() throws SQLException {
        try (Connection connection = oraclePdbWithConfiguration.getPdbDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create table TEST_TABLE (\n" +
                    "    ID_ number(19,0) not null,\n" +
                    "    NAME_ varchar2(255 char),\n" +
                    "    primary key (ID_)\n" +
                    ")");
        }
    }

    @Test
    public void eagerPdbCreationTest() throws SQLException {
        try (Connection connection = oraclePdbWithConfiguration.getPdbDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SELECT * FROM TEST_TABLE");
        }
    }

    @Test
    public void usernamePasswordJdbcUrlTest() throws SQLException {
        final OracleDataSource pdbDataSource = oraclePdbWithoutConfiguration.getPdbDataSource();
        Assert.assertEquals(oraclePdbWithoutConfiguration.getPdbAdminUser(), pdbDataSource.getUser());
        Assert.assertNotNull(oraclePdbWithoutConfiguration.getPdbAdminPassword());
        Assert.assertNotNull(oraclePdbWithoutConfiguration.getPdbJdbcUrl());
    }

    @Test
    public void withoutFurtherConfigurationTest() throws SQLException {
        try (Connection connection = oraclePdbWithoutConfiguration.getPdbDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1 FROM DUAL");
        }
    }
}
