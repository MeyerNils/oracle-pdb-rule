package bayern.meyer.junit.rules

import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class OraclePdbConfiguration private constructor(
        val cdbJdbcUrl: String,
        val cdbUsername: String,
        val cdbPassword: String,
        val keepPdb: Boolean,
        val createPdb: Boolean,
        val createPdbEager: Boolean,
        val grantUnlimitedTablespace: Boolean,
        val pdbSeedPath: String,
        private val pdbBasePath: String
) {
    val pdbName: String = sessionIdentifier + pdbCount.getAndIncrement()
    val pdbPath: String = "$pdbBasePath$pdbName/"

    /**
     * Get the password to access the created PDB as admin
     * If CREATE_PDB is set to `false`, the CDB_PASSWORD is returned
     *
     * @return The password to access the test database
     */
    val pdbAdminPassword = randomAlphabetic(12).toUpperCase()
        get() = if (createPdb) field else cdbPassword

    /**
     * Get the username to access the created PDB as admin
     * If CREATE_PDB is set to `false`, the CDB_USERNAME is returned
     *
     * @return The username to access the test database
     */
    val pdbAdminUser = randomAlphabetic(12).toUpperCase()
        get() = if (createPdb) field else cdbUsername

    /**
     * Get the JDBC URL to access the created PDB
     * If CREATE_PDB is set to `false`, the CDB JDBC URL is returned
     *
     * @return The JDBC URL to access the test database
     */
    val pdbJdbcUrl: String
        get() = if (createPdb) calculatePdbJdbcUrl() else cdbJdbcUrl

    private fun calculatePdbJdbcUrl(): String {
        return cdbJdbcUrl.substring(0, cdbJdbcUrl.lastIndexOf("/") + 1) + pdbName
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(OraclePdbConfiguration::class.java)
        private val random = Random()
        private val sessionIdentifier = randomAlphabetic(6).toUpperCase()
        private val pdbCount = AtomicInteger(1)
        private fun randomAlphabetic(length: Int): String {
            val leftLimit = 65 // letter 'A'
            val rightLimit = 90 // letter 'Z'
            return random.ints(leftLimit, rightLimit + 1)
                    .limit(length.toLong())
                    .collect({ StringBuilder() }, { obj: StringBuilder, codePoint: Int -> obj.appendCodePoint(codePoint) }) { obj: StringBuilder, s: StringBuilder? -> obj.append(s) }
                    .toString()
        }
    }

    class Builder() {
        private var oradataFolder = System.getProperty(PropertyKeys.ORADATA_FOLDER, "/opt/oracle/oradata/")
        private var cdbName = System.getProperty(PropertyKeys.CDB_NAME, "ORCLCDB")
        private var cdbHost = System.getProperty(PropertyKeys.CDB_HOST, "localhost")
        private var cdbPort = System.getProperty(PropertyKeys.CDB_PORT, "1521")
        private var pdbSeedName = System.getProperty(PropertyKeys.PDBSEED_NAME, "pdbseed")
        private var cdbUsername = System.getProperty(PropertyKeys.CDB_USERNAME, "sys as sysdba")
        private var cdbPassword = System.getProperty(PropertyKeys.CDB_PASSWORD, "oracle")
        private var createPdb = java.lang.Boolean.parseBoolean(System.getProperty(PropertyKeys.CREATE_PDB, "true"))
        private var keepPdb = java.lang.Boolean.parseBoolean(System.getProperty(PropertyKeys.KEEP_PDB, "false"))
        private var createPdbEager = java.lang.Boolean.parseBoolean(System.getProperty(PropertyKeys.CREATE_PDB_EAGER, "false"))
        private var grantUnlimitedTablespace = java.lang.Boolean.parseBoolean(System.getProperty(PropertyKeys.GRANT_UNLIMITED_TABLESPACE, "false"))
        private var customCdbJdbcUrl: String? = null
        private var customPdbBasePath: String? = null
        private var customPdbSeedPath: String? = null

        /**
         * Use this method to create a PDB already on rule initialization and not lazy on [TestRule.apply]; this can be helpful if other rules, e.g. Flyway, rely on an existing database
         * If existing, property CREATE_PDB_EAGER has precedence over using this method. CREATE_PDB_EAGER defaults to `false`.
         *
         * @return `this`
         */
        fun andCreatePdbEager() = applyIfPropertyIsNotSet(PropertyKeys.CREATE_PDB_EAGER) { createPdbEager = true }

        /**
         * Use this method to skip PDB creation and use the database credentials provided for the CDB access to access the test database. This mode can be helpful to test locally without spinning up a new database over and over again.
         * If existing, property CREATE_PDB has precedence over using this method. CREATE_PDB defaults to `true`.
         *
         * @return `this`
         */
        fun andDoNotCreatePdb() = applyIfPropertyIsNotSet(PropertyKeys.CREATE_PDB) { createPdb = false }

        /**
         * Use this method to skip PDB deletion after the tests have finished. This mode can be helpful to locally verify the database content after tests have been executed.
         * If existing, property KEEP_PDB has precedence over using this method. KEEP_PDB defaults to `false`.
         *
         * @return `this`
         */
        fun andKeepPdb() = applyIfPropertyIsNotSet(PropertyKeys.KEEP_PDB) { keepPdb = true }

        /**
         * Use this method to grant unlimited table space to the PDB admin user (executing "GRANT UNLIMITED TABLESPACE TO <PDB_ADMIN_USER>")
         * If existing, property GRANT_UNLIMITED_TABLESPACE has precedence over using this method. GRANT_UNLIMITED_TABLESPACE defaults to `false`.
         *
         * @return `this`
         */
        fun grantUnlimitedTablespace() = applyIfPropertyIsNotSet(PropertyKeys.GRANT_UNLIMITED_TABLESPACE) { grantUnlimitedTablespace = true }

        /**
         * Used to specify access to the CDB.
         * If existing, property CDB_HOST has precedence over using this method.
         * Defaults to `localhost`
         *
         * @param cdbHost The username to access the CDB
         * @return `this`
         */
        fun withCdbHost(cdbHost: String) = applyIfPropertyIsNotSet(PropertyKeys.CDB_HOST) { this.cdbHost = cdbHost }


        /**
         * Used to specify access to the CDB.
         * If existing, property CDB_JDBC_URL has precedence over using this method.
         * Defaults to jdbc:oracle:thin:@${CDB_HOST}:${CDB_PORT}/${CDB_NAME} / jdbc:oracle:thin:@localhost:1521/ORCLCDB if not set.
         *
         * @param cdbJdbcUrl The JDBC URL to access the CDB
         * @return `this`
         */
        fun withCdbJdbcUrl(cdbJdbcUrl: String) = applyIfPropertyIsNotSet(PropertyKeys.CDB_JDBC_URL) { customCdbJdbcUrl = cdbJdbcUrl }


        /**
         * Used to specify access to the CDB.
         * If existing, property CDB_NAME has precedence over using this method.
         * Defaults to `ORCLCDB`
         *
         * @param cdbName The username to access the CDB
         * @return `this`
         */
        fun withCdbName(cdbName: String) = applyIfPropertyIsNotSet(PropertyKeys.CDB_NAME) { this.cdbName = cdbName }

        /**
         * Used to specify access to the CDB.
         * If existing, property CDB_PASSWORD has precedence over using this method.
         * Defaults to `oracle`
         *
         * @param cdbPassword The password to access the CDB
         * @return `this`
         */
        fun withCdbPassword(cdbPassword: String) = applyIfPropertyIsNotSet(PropertyKeys.CDB_PASSWORD) { this.cdbPassword = cdbPassword }

        /**
         * Used to specify access to the CDB.
         * If existing, property CDB_PORT has precedence over using this method.
         * Defaults to `1521`
         *
         * @param cdbPort The username to access the CDB
         * @return `this`
         */
        fun withCdbPort(cdbPort: String) = applyIfPropertyIsNotSet(PropertyKeys.CDB_PORT) { this.cdbPort = cdbPort }

        /**
         * Used to specify access to the CDB.
         * If existing, property CDB_USERNAME has precedence over using this method.
         * Defaults to `sys as sysdba`
         *
         * @param cdbUsername The username to access the CDB
         * @return `this`
         */
        fun withCdbUsername(cdbUsername: String) = applyIfPropertyIsNotSet(PropertyKeys.CDB_USERNAME) { this.cdbUsername = cdbUsername }

        /**
         * Used to specify base folder for PDB creation
         * If existing, property ORADATA_FOLDER has precedence over using this method.
         * Defaults to `/opt/oracle/oradata/`
         *
         * @param oradataFolder The folder where pdbSeed and pdb folder are and will be located
         * @return `this`
         */
        fun withOradataFolder(oradataFolder: String) = applyIfPropertyIsNotSet(PropertyKeys.ORADATA_FOLDER) { this.oradataFolder = oradataFolder }

        /**
         * Used to specify the path where the folder for the PDB should be created
         * If existing, property PDB_BASE_PATH has precedence over using this method.
         * Defaults to `${ORADATA}/${CDBNAME}/` - `/opt/oracle/oradata/ORCLCDB/`.
         * Instead of setting this property directly the properties mentioned in the default value can be set.
         *
         * @param pdbBasePath The folder where the PDB folder will be created
         * @return `this`
         */
        fun withPdbBasePath(pdbBasePath: String?) = applyIfPropertyIsNotSet(PropertyKeys.PDB_BASE_PATH) { customPdbBasePath = pdbBasePath }

        /**
         * Used to specify the name of the seed PDB
         * If existing, property PDBSEED_NAME has precedence over using this method.
         * Defaults to `pdbseed`
         *
         * @param pdbSeedName The name of the seed PDB
         * @return `this`
         */
        fun withPdbSeedName(pdbSeedName: String) = applyIfPropertyIsNotSet(PropertyKeys.PDBSEED_NAME) { this.pdbSeedName = pdbSeedName }

        /**
         * Used to specify the complete path for the seed PDB
         * If existing, property PDB_SEED_PATH has precedence over using this method.
         * Defaults to `${ORADATA}/${CDBNAME}/${PDBSEED_NAME}/` - `/opt/oracle/oradata/ORCLCDB/pdbseed/`.
         * Instead of setting this property directly the properties mentioned in the default value can be set.
         *
         * @param pdbSeedPath The folder where the PDB seed is located
         * @return `this`
         */
        fun withPdbSeedPath(pdbSeedPath: String?) = applyIfPropertyIsNotSet(PropertyKeys.PDB_SEED_PATH) { customPdbSeedPath = pdbSeedPath }

        private fun applyIfPropertyIsNotSet(propertyKey: String, function: () -> Unit) =
                apply {
                    System.getProperty(propertyKey)?.let {
                        LOGGER.debug("Value of property $propertyKey is taking precedence over corresponding programmatically set property.")
                    } ?: function()
                }

        fun build() = OraclePdbConfiguration(
                cdbJdbcUrl = System.getProperty(PropertyKeys.CDB_JDBC_URL, if (customCdbJdbcUrl != null) customCdbJdbcUrl else "jdbc:oracle:thin:@$cdbHost:$cdbPort/$cdbName"),
                cdbUsername = cdbUsername,
                cdbPassword = cdbPassword,
                keepPdb = keepPdb,
                createPdb = createPdb,
                createPdbEager = createPdbEager,
                grantUnlimitedTablespace = grantUnlimitedTablespace,
                pdbSeedPath = System.getProperty(PropertyKeys.PDB_SEED_PATH, if (customPdbSeedPath != null) customPdbSeedPath else "$oradataFolder$cdbName/$pdbSeedName/"),
                pdbBasePath = System.getProperty(PropertyKeys.PDB_BASE_PATH, if (customPdbBasePath != null) customPdbBasePath else "$oradataFolder$cdbName/")
        )
    }
}

object PropertyKeys {
    const val GRANT_UNLIMITED_TABLESPACE = "GRANT_UNLIMITED_TABLESPACE"
    const val CREATE_PDB_EAGER = "CREATE_PDB_EAGER"
    const val ORADATA_FOLDER = "ORADATA_FOLDER"
    const val CDB_NAME = "CDB_NAME"
    const val CDB_HOST = "CDB_HOST"
    const val CDB_PORT = "CDB_PORT"
    const val PDBSEED_NAME = "PDBSEED_NAME"
    const val CDB_USERNAME = "CDB_USERNAME"
    const val CDB_PASSWORD = "CDB_PASSWORD"
    const val CREATE_PDB = "CREATE_PDB"
    const val KEEP_PDB = "KEEP_PDB"
    const val CDB_JDBC_URL = "CDB_JDBC_URL"
    const val PDB_BASE_PATH = "PDB_BASE_PATH"
    const val PDB_SEED_PATH = "PDB_SEED_PATH"
}
