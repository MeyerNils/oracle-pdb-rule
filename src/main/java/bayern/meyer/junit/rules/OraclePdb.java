package bayern.meyer.junit.rules;

import com.pivovarit.function.ThrowingRunnable;
import com.pivovarit.function.ThrowingSupplier;
import oracle.jdbc.pool.OracleDataSource;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class OraclePdb implements TestRule {
    private static final Logger LOGGER = LoggerFactory.getLogger(OraclePdb.class);
    private static RemovePdbShutdownHook removePdbShutdownHook;
    private final OraclePdbConfiguration oraclePdbConfiguration;
    private OracleDataSource oracleCdbDataSource;
    private OracleDataSource oraclePdbDataSource;
    private boolean pdbCreated = false;

    public OraclePdb() {
        this(new OraclePdbConfiguration());
    }

    public OraclePdb(final OraclePdbConfiguration oraclePdbConfiguration) {
        this.oraclePdbConfiguration = oraclePdbConfiguration;
        if (oraclePdbConfiguration.createPdbEager)
            ThrowingRunnable.sneaky(this::createPdbIfNeeded).run();
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                createPdbIfNeeded();
                statement.evaluate();
            }
        };
    }

    /**
     * Get the DataSource to access the created PDB as admin
     * If CREATE_PDB is set to <code>false</code>, the CDB_PASSWORD is returned
     *
     * @return The DataSource to access the test database
     */
    public DataSource getPdbDataSource() {
        return oraclePdbDataSource;
    }

    private void createPdbIfNeeded() throws SQLException {
        if (oraclePdbConfiguration.createPdb && !pdbCreated) {
            final OracleDataSource cdbDataSource = getCdbDataSource();
            try (Connection connection = cdbDataSource.getConnection();
                 java.sql.Statement cdbStatement = connection.createStatement()) {
                cdbStatement.execute("alter session set container = CDB$ROOT");
                cdbStatement.execute("CREATE PLUGGABLE DATABASE " + oraclePdbConfiguration.pdbName + " ADMIN USER " + oraclePdbConfiguration.pdbAdminUser + " IDENTIFIED BY " + oraclePdbConfiguration.pdbAdminPassword + " ROLES=(DBA) FILE_NAME_CONVERT=('" + oraclePdbConfiguration.getPdbSeedPath() + "','" + oraclePdbConfiguration.getPdbPath() + "')");
                cdbStatement.execute("alter pluggable database " + oraclePdbConfiguration.pdbName + " open");
                LOGGER.info("Created PDB {} with admin user {} - connect using {}", oraclePdbConfiguration.pdbName, oraclePdbConfiguration.pdbAdminUser, oraclePdbConfiguration.calculatePdbJdbcUrl(oraclePdbConfiguration.getCdbJdbcUrl()));
                oraclePdbDataSource = new OracleDataSource();
                oraclePdbDataSource.setURL(oraclePdbConfiguration.getPdbJdbcUrl());
                oraclePdbDataSource.setUser(oraclePdbConfiguration.getPdbAdminUser());
                oraclePdbDataSource.setPassword(oraclePdbConfiguration.getPdbAdminPassword());
                if (oraclePdbConfiguration.grantUnlimitedTablespace) {
                    grantUnlimitedTablespace();
                }
                if (!oraclePdbConfiguration.keepPdb)
                    getShutdownHook().registerPdbToRemove(oraclePdbConfiguration.pdbName);
                pdbCreated = true;
            }
        }
    }

    private OracleDataSource getCdbDataSource() {
        if (oracleCdbDataSource == null) {
            oracleCdbDataSource = ThrowingSupplier.sneaky(OracleDataSource::new).get();
            oracleCdbDataSource.setURL(oraclePdbConfiguration.getCdbJdbcUrl());
            oracleCdbDataSource.setUser(oraclePdbConfiguration.cdbUsername);
            oracleCdbDataSource.setPassword(oraclePdbConfiguration.cdbPassword);
            LOGGER.info("Initialized CDB database connection using {} and username {}", oraclePdbConfiguration.getCdbJdbcUrl(), oraclePdbConfiguration.cdbUsername);
        }
        return oracleCdbDataSource;
    }

    /**
     * Get the password to access the created PDB as admin
     * If CREATE_PDB is set to <code>false</code>, the CDB_PASSWORD is returned
     *
     * @return The password to access the test database
     */
    public String getPdbAdminPassword() {
        return oraclePdbConfiguration.getPdbAdminPassword();
    }

    /**
     * Get the username to access the created PDB as admin
     * If CREATE_PDB is set to <code>false</code>, the CDB_USERNAME is returned
     *
     * @return The username to access the test database
     */
    public String getPdbAdminUser() {
        return oraclePdbConfiguration.getPdbAdminUser();
    }

    /**
     * Get the JDBC URL to access the created PDB
     * If CREATE_PDB is set to <code>false</code>, the CDB JDBC URL is returned
     *
     * @return The JDBC URL to access the test database
     */
    public String getPdbJdbcUrl() {
        return oraclePdbConfiguration.getPdbJdbcUrl();
    }

    private RemovePdbShutdownHook getShutdownHook() {
        if (removePdbShutdownHook == null) {
            removePdbShutdownHook = new RemovePdbShutdownHook();
            Runtime.getRuntime().addShutdownHook(removePdbShutdownHook);
            LOGGER.debug("Remove PDB shutdown hook is registered.");
        }
        return removePdbShutdownHook;
    }

    private void grantUnlimitedTablespace() {
        ThrowingRunnable.sneaky(() -> {
            try (Connection connection = oraclePdbDataSource.getConnection(); java.sql.Statement statement = connection.createStatement()) {
                // statement.execute("CREATE TABLESPACE FLYWAY DATAFILE 'flyway_001.dbf' SIZE 2M AUTOEXTEND ON NEXT 2M MAXSIZE UNLIMITED EXTENT MANAGEMENT LOCAL SEGMENT SPACE MANAGEMENT AUTO");
                statement.execute("GRANT UNLIMITED TABLESPACE TO " + oraclePdbConfiguration.getPdbAdminUser());
            }
        }).run();
    }

    public static class OraclePdbConfiguration {
        private static final Random random = new Random();
        private static final String sessionIdentifier = randomAlphabetic(6).toUpperCase();
        private static final AtomicInteger pdbCount = new AtomicInteger(1);
        private final String pdbName;
        private String cdbHost;
        private String cdbName;
        private String cdbPassword;
        private String cdbPort;
        private String cdbUsername;
        private boolean createPdb;
        private boolean createPdbEager = false;
        private String customCdbJdbcUrl = null;
        private String customPdbBasePath = null;
        private String customPdbSeedPath = null;
        private boolean grantUnlimitedTablespace = false;
        private boolean keepPdb;
        private String oradataFolder;
        private String pdbAdminPassword = randomAlphabetic(12).toUpperCase();
        private String pdbAdminUser = randomAlphabetic(12).toUpperCase();
        private String pdbSeedName;

        public OraclePdbConfiguration() {
            oradataFolder = System.getProperty(PropertyKeys.ORADATA_FOLDER, "/opt/oracle/oradata");
            cdbName = System.getProperty(PropertyKeys.CDB_NAME, "ORCLCDB");
            cdbHost = System.getProperty(PropertyKeys.CDB_HOST, "localhost");
            cdbPort = System.getProperty(PropertyKeys.CDB_PORT, "1521");
            pdbSeedName = System.getProperty(PropertyKeys.PDBSEED_NAME, "pdbseed");
            cdbUsername = System.getProperty(PropertyKeys.CDB_USERNAME, "sys as sysdba");
            cdbPassword = System.getProperty(PropertyKeys.CDB_PASSWORD, "oracle");
            pdbName = sessionIdentifier + pdbCount.getAndIncrement();
            createPdb = Boolean.parseBoolean(System.getProperty(PropertyKeys.CREATE_PDB, "true"));
            keepPdb = Boolean.parseBoolean(System.getProperty(PropertyKeys.KEEP_PDB, "false"));
            createPdbEager = Boolean.parseBoolean(System.getProperty(PropertyKeys.CREATE_PDB_EAGER, "false"));
            grantUnlimitedTablespace = Boolean.parseBoolean(System.getProperty(PropertyKeys.GRANT_UNLIMITED_TABLESPACE, "false"));
        }

        private static String randomAlphabetic(final int length) {
            int leftLimit = 65; // letter 'A'
            int rightLimit = 90; // letter 'Z'

            return random.ints(leftLimit, rightLimit + 1)
                    .limit(length)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
        }

        /**
         * Use this method to create a PDB already on rule initialization and not lazy on {@link TestRule#apply(Statement, Description)}; this can be helpful if other rules, e.g. Flyway, rely on an existing database
         * If existing, property CREATE_PDB_EAGER has precedence over using this method. CREATE_PDB_EAGER defaults to <code>false</code>.
         *
         * @return <code>this</code>
         */
        public OraclePdbConfiguration andCreatePdbEager() {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.CREATE_PDB_EAGER))
                this.createPdbEager = true;
            return this;
        }

        /**
         * Use this method to skip PDB creation and use the database credentials provided for the CDB access to access the test database. This mode can be helpful to test locally without spinning up a new database over and over again.
         * If existing, property CREATE_PDB has precedence over using this method. CREATE_PDB defaults to <code>true</code>.
         *
         * @return <code>this</code>
         */
        public OraclePdbConfiguration andDoNotCreatePdb() {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.CREATE_PDB))
                this.createPdb = false;
            return this;
        }

        /**
         * Use this method to skip PDB deletion after the tests have finished. This mode can be helpful to locally verify the database content after tests have been executed.
         * If existing, property KEEP_PDB has precedence over using this method. KEEP_PDB defaults to <code>false</code>.
         *
         * @return <code>this</code>
         */
        public OraclePdbConfiguration andKeepPdb() {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.KEEP_PDB))
                this.keepPdb = true;
            return this;
        }

        private String calculatePdbJdbcUrl(String cdbJdbcUrl) {
            return cdbJdbcUrl.substring(0, cdbJdbcUrl.lastIndexOf("/") + 1) + pdbName;
        }

        private String getCdbJdbcUrl() {
            return System.getProperty(OraclePdbConfiguration.PropertyKeys.CDB_JDBC_URL, customCdbJdbcUrl != null ? customCdbJdbcUrl : "jdbc:oracle:thin:@" + cdbHost + ":" + cdbPort + "/" + cdbName);
        }

        /**
         * Get the password to access the created PDB as admin
         * If CREATE_PDB is set to <code>false</code>, the CDB_PASSWORD is returned
         *
         * @return The password to access the test database
         */
        public String getPdbAdminPassword() {
            return createPdb ? pdbAdminPassword : cdbPassword;
        }

        /**
         * Get the username to access the created PDB as admin
         * If CREATE_PDB is set to <code>false</code>, the CDB_USERNAME is returned
         *
         * @return The username to access the test database
         */
        public String getPdbAdminUser() {
            return createPdb ? pdbAdminUser : cdbUsername;
        }

        /**
         * Get the JDBC URL to access the created PDB
         * If CREATE_PDB is set to <code>false</code>, the CDB JDBC URL is returned
         *
         * @return The JDBC URL to access the test database
         */
        public String getPdbJdbcUrl() {
            return createPdb ? calculatePdbJdbcUrl(getCdbJdbcUrl()) : getCdbJdbcUrl();
        }

        private String getPdbPath() {
            return System.getProperty(OraclePdbConfiguration.PropertyKeys.PDB_BASE_PATH, customPdbBasePath != null ? customPdbBasePath : oradataFolder + "/" + cdbName + "/") + pdbName + "/";
        }

        private String getPdbSeedPath() {
            return System.getProperty(OraclePdbConfiguration.PropertyKeys.PDB_SEED_PATH, customPdbSeedPath != null ? customPdbSeedPath : oradataFolder + "/" + cdbName + "/" + pdbSeedName + "/");
        }

        /**
         * Use this method to grant unlimited table space to the PDB admin user (executing "GRANT UNLIMITED TABLESPACE TO <PDB_ADMIN_USER>")
         * If existing, property GRANT_UNLIMITED_TABLESPACE has precedence over using this method. GRANT_UNLIMITED_TABLESPACE defaults to <code>false</code>.
         *
         * @return <code>this</code>
         */
        public OraclePdbConfiguration grantUnlimitedTablespace() {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.GRANT_UNLIMITED_TABLESPACE))
                this.grantUnlimitedTablespace = true;
            return this;
        }

        private boolean propertyIsNotSet(String propertyKey) {
            if (System.getProperty(propertyKey) != null) {
                LOGGER.debug("Value of property {}} is taking precedence over corresponding  programmatically set property.", propertyKey);
                return false;
            } else {
                return true;
            }
        }

        /**
         * Used to specify access to the CDB.
         * If existing, property CDB_HOST has precedence over using this method.
         * Defaults to <code>localhost</code>
         *
         * @param cdbHost The username to access the CDB
         * @return <code>this</code>
         */
        public OraclePdbConfiguration withCdbHost(final String cdbHost) {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.CDB_HOST))
                this.cdbHost = cdbHost;
            return this;
        }

        /**
         * Used to specify access to the CDB.
         * If existing, property CDB_JDBC_URL has precedence over using this method.
         * Defaults to jdbc:oracle:thin:@${CDB_HOST}:${CDB_PORT}/${CDB_NAME} / jdbc:oracle:thin:@localhost:1521/ORCLCDB if not set.
         *
         * @param cdbJdbcUrl The JDBC URL to access the CDB
         * @return <code>this</code>
         */
        public OraclePdbConfiguration withCdbJdbcUrl(final String cdbJdbcUrl) {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.CDB_JDBC_URL))
                this.customCdbJdbcUrl = cdbJdbcUrl;
            return this;
        }

        /**
         * Used to specify access to the CDB.
         * If existing, property CDB_NAME has precedence over using this method.
         * Defaults to <code>ORCLCDB</code>
         *
         * @param cdbName The username to access the CDB
         * @return <code>this</code>
         */
        public OraclePdbConfiguration withCdbName(final String cdbName) {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.CDB_NAME))
                this.cdbName = cdbName;
            return this;
        }

        /**
         * Used to specify access to the CDB.
         * If existing, property CDB_PASSWORD has precedence over using this method.
         * Defaults to <code>oracle</code>
         *
         * @param cdbPassword The password to access the CDB
         * @return <code>this</code>
         */
        public OraclePdbConfiguration withCdbPassword(final String cdbPassword) {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.CDB_PASSWORD))
                this.cdbPassword = cdbPassword;
            return this;
        }

        /**
         * Used to specify access to the CDB.
         * If existing, property CDB_PORT has precedence over using this method.
         * Defaults to <code>1521</code>
         *
         * @param cdbPort The username to access the CDB
         * @return <code>this</code>
         */
        public OraclePdbConfiguration withCdbPort(final String cdbPort) {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.CDB_PORT))
                this.cdbPort = cdbPort;
            return this;
        }

        /**
         * Used to specify access to the CDB.
         * If existing, property CDB_USERNAME has precedence over using this method.
         * Defaults to <code>sys as sysdba</code>
         *
         * @param cdbUsername The username to access the CDB
         * @return <code>this</code>
         */
        public OraclePdbConfiguration withCdbUsername(final String cdbUsername) {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.CDB_USERNAME))
                this.cdbUsername = cdbUsername;
            return this;
        }

        /**
         * Used to specify base folder for PDB creation
         * If existing, property ORADATA_FOLDER has precedence over using this method.
         * Defaults to <code>/opt/oracle/oradata</code>
         *
         * @param oradataFolder The folder where pdbSeed and pdb folder are and will be located
         * @return <code>this</code>
         */
        public OraclePdbConfiguration withOradataFolder(final String oradataFolder) {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.ORADATA_FOLDER))
                this.oradataFolder = oradataFolder;
            return this;
        }

        /**
         * Used to specify the path where the folder for the PDB should be created
         * If existing, property PDB_BASE_PATH has precedence over using this method.
         * Defaults to <code>${ORADATA}/${CDBNAME}/</code> - <code>/opt/oracle/oradata/ORCLCDB/</code>.
         * Instead of setting this property directly the properties mentioned in the default value can be set.
         *
         * @param pdbBasePath The folder where the PDB folder will be created
         * @return <code>this</code>
         */
        public OraclePdbConfiguration withPdbBasePath(final String pdbBasePath) {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.PDB_BASE_PATH))
                this.customPdbBasePath = pdbBasePath;
            return this;
        }

        /**
         * Used to specify the name of the seed PDB
         * If existing, property PDBSEED_NAME has precedence over using this method.
         * Defaults to <code>pdbseed</code>
         *
         * @param pdbSeedName The name of the seed PDB
         * @return <code>this</code>
         */
        public OraclePdbConfiguration withPdbSeedName(final String pdbSeedName) {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.PDBSEED_NAME))
                this.pdbSeedName = pdbSeedName;
            return this;
        }

        /**
         * Used to specify the complete path for the seed PDB
         * If existing, property PDB_SEED_PATH has precedence over using this method.
         * Defaults to <code>${ORADATA}/${CDBNAME}/${PDBSEED_NAME}/</code> - <code>/opt/oracle/oradata/ORCLCDB/pdbseed/</code>.
         * Instead of setting this property directly the properties mentioned in the default value can be set.
         *
         * @param pdbSeedPath The folder where the PDB seed is located
         * @return <code>this</code>
         */
        public OraclePdbConfiguration withPdbSeedPath(final String pdbSeedPath) {
            if (propertyIsNotSet(OraclePdbConfiguration.PropertyKeys.PDB_SEED_PATH))
                this.customPdbSeedPath = pdbSeedPath;
            return this;
        }

        private static class PropertyKeys {
            private static final String GRANT_UNLIMITED_TABLESPACE = "GRANT_UNLIMITED_TABLESPACE";
            private static final String CREATE_PDB_EAGER = "CREATE_PDB_EAGER";
            private static final String ORADATA_FOLDER = "ORADATA_FOLDER";
            private static final String CDB_NAME = "CDB_NAME";
            private static final String CDB_HOST = "CDB_HOST";
            private static final String CDB_PORT = "CDB_PORT";
            private static final String PDBSEED_NAME = "PDBSEED_NAME";
            private static final String CDB_USERNAME = "CDB_USERNAME";
            private static final String CDB_PASSWORD = "CDB_PASSWORD";
            private static final String CREATE_PDB = "CREATE_PDB";
            private static final String KEEP_PDB = "KEEP_PDB";
            private static final String CDB_JDBC_URL = "CDB_JDBC_URL";
            private static final String PDB_BASE_PATH = "PDB_BASE_PATH";
            private static final String PDB_SEED_PATH = "PDB_SEED_PATH";
        }
    }

    private class RemovePdbShutdownHook extends Thread {
        private final List<String> pdbsToRemove = new ArrayList<>();

        public void registerPdbToRemove(String pdbName) {
            pdbsToRemove.add(pdbName);
        }

        private void removePdbs() throws SQLException {
            final OracleDataSource cdbDataSource = getCdbDataSource();
            try (Connection connection = cdbDataSource.getConnection();
                 java.sql.Statement cdbStatement = connection.createStatement()) {
                for (String pdbName : pdbsToRemove) {
                    cdbStatement.execute("alter session set container = CDB$ROOT");
                    cdbStatement.execute("alter pluggable database " + pdbName + " close immediate");
                    cdbStatement.execute("DROP PLUGGABLE DATABASE " + pdbName + " INCLUDING DATAFILES");
                    LOGGER.info("Removed PDB {}", pdbName);
                }
            }
        }

        @Override
        public void run() {
            LOGGER.info("Starting to remove PDBs that were created...");
            ThrowingRunnable.sneaky(this::removePdbs).run();
        }
    }
}
