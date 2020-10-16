package bayern.meyer.junit.rules

import oracle.jdbc.pool.OracleDataSource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.slf4j.LoggerFactory
import java.util.*
import javax.sql.DataSource

class OraclePdb @JvmOverloads constructor(private val oraclePdbConfiguration: OraclePdbConfiguration = OraclePdbConfiguration.Builder().build()) : TestRule {
    private val cdbDataSource = initializeCdbDataSource()

    /**
     * Get the JDBC URL to access the created PDB
     * If CREATE_PDB is set to `false`, the CDB JDBC URL is returned
     *
     * @return The JDBC URL to access the test database
     */
    val pdbJdbcUrl: String
        get() = if (oraclePdbConfiguration.createPdb) calculatePdbJdbcUrl() else oraclePdbConfiguration.cdbJdbcUrl


    /**
     * Get the username to access the created PDB as admin
     * If CREATE_PDB is set to `false`, the CDB_USERNAME is returned
     *
     * @return The username to access the test database
     */
    val pdbAdminUser: String
        get() = oraclePdbConfiguration.pdbAdminUser

    /**
     * Get the password to access the created PDB as admin
     * If CREATE_PDB is set to `false`, the CDB_PASSWORD is returned
     *
     * @return The password to access the test database
     */
    val pdbAdminPassword: String
        get() = oraclePdbConfiguration.pdbAdminPassword

    /**
     * Get the DataSource to access the created PDB as admin
     * If CREATE_PDB is set to `false`, the CDB_PASSWORD is returned
     *
     * @return The DataSource to access the test database
     */
    val pdbDataSource by lazy { initializePdbDataSource() }

    private var pdbCreated = false
    private lateinit var pdbServiceName: String;

    override fun apply(statement: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                createPdbIfNeeded()
                statement.evaluate()
            }
        }
    }

    private fun calculatePdbJdbcUrl(): String {
        return oraclePdbConfiguration.cdbJdbcUrl.substring(0, oraclePdbConfiguration.cdbJdbcUrl.lastIndexOf("/") + 1) + pdbServiceName
    }

    private fun createPdbIfNeeded() {
        if (oraclePdbConfiguration.createPdb && !pdbCreated) {
            cdbDataSource.connection.use { connection ->
                connection.createStatement().use { cdbStatement ->
                    cdbStatement.execute("ALTER SESSION SET CONTAINER = CDB\$ROOT")
                    cdbStatement.execute("CREATE PLUGGABLE DATABASE ${oraclePdbConfiguration.pdbName} ADMIN USER ${oraclePdbConfiguration.pdbAdminUser} IDENTIFIED BY ${oraclePdbConfiguration.pdbAdminPassword} ROLES=(DBA) FILE_NAME_CONVERT=('${oraclePdbConfiguration.pdbSeedPath}','${oraclePdbConfiguration.pdbPath}')")
                    cdbStatement.execute("ALTER PLUGGABLE DATABASE ${oraclePdbConfiguration.pdbName} OPEN")
                    cdbStatement.execute("ALTER SESSION SET CONTAINER = ${oraclePdbConfiguration.pdbName}")
                    cdbStatement.execute("SELECT sys_context('userenv','service_name') FROM dual")
                    pdbServiceName = cdbStatement.resultSet.use {
                        if (it.next()) it.getString(1) else oraclePdbConfiguration.pdbName
                    }
                    LOGGER.info("Created PDB ${oraclePdbConfiguration.pdbName} with admin user ${oraclePdbConfiguration.pdbAdminUser} - connect using $pdbJdbcUrl")
                    if (!oraclePdbConfiguration.keepPdb)
                        removePdbShutdownHook.registerPdbToRemove(Pair(oraclePdbConfiguration.pdbName, cdbDataSource))
                    if (oraclePdbConfiguration.grantUnlimitedTablespace)
                        grantUnlimitedTablespace()
                    pdbCreated = true
                }
            }
        }
    }

    private fun initializePdbDataSource(): OracleDataSource {
        val oracleDataSource = OracleDataSource()
        oracleDataSource.url = pdbJdbcUrl
        oracleDataSource.user = oraclePdbConfiguration.pdbAdminUser
        oracleDataSource.setPassword(oraclePdbConfiguration.pdbAdminPassword)
        return oracleDataSource
    }

    private fun initializeCdbDataSource(): OracleDataSource {
        val oracleDataSource = OracleDataSource()
        oracleDataSource.url = oraclePdbConfiguration.cdbJdbcUrl
        oracleDataSource.user = oraclePdbConfiguration.cdbUsername
        oracleDataSource.setPassword(oraclePdbConfiguration.cdbPassword)
        LOGGER.info("Initialized CDB database connection using ${oraclePdbConfiguration.cdbJdbcUrl} and username ${oraclePdbConfiguration.cdbUsername}")
        return oracleDataSource
    }

    private fun grantUnlimitedTablespace() {
        pdbDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("GRANT UNLIMITED TABLESPACE TO ${oraclePdbConfiguration.pdbAdminUser}")
            }
        }
    }

    private class RemovePdbShutdownHook : Thread() {
        private val pdbsToRemove: MutableList<Pair<String, DataSource>> = ArrayList()
        fun registerPdbToRemove(pdbNameDatasSourcePair: Pair<String, DataSource>) {
            pdbsToRemove.add(pdbNameDatasSourcePair)
        }

        private fun removePdbs() {
            for (pdbNameDatasSourcePair in pdbsToRemove) {
                val cdbDataSource = pdbNameDatasSourcePair.second
                cdbDataSource.connection.use { connection ->
                    connection.createStatement().use { cdbStatement ->
                        cdbStatement.execute("ALTER SESSION SET CONTAINER = CDB\$ROOT")
                        cdbStatement.execute("ALTER PLUGGABLE DATABASE ${pdbNameDatasSourcePair.first} CLOSE IMMEDIATE")
                        cdbStatement.execute("DROP PLUGGABLE DATABASE ${pdbNameDatasSourcePair.first} INCLUDING DATAFILES")
                        LOGGER.info("Removed PDB ${pdbNameDatasSourcePair.first}")
                    }
                }
            }
        }

        override fun run() {
            LOGGER.info("Starting to remove PDBs that were created...")
            removePdbs()
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(OraclePdb::class.java)
        private val removePdbShutdownHook = initializeShutdownHook()

        private fun initializeShutdownHook(): RemovePdbShutdownHook {
            val shutdownHook = RemovePdbShutdownHook()
            Runtime.getRuntime().addShutdownHook(shutdownHook)
            LOGGER.debug("Remove PDB shutdown hook is registered.")
            return shutdownHook
        }
    }

    init {
        if (oraclePdbConfiguration.createPdbEager)
            createPdbIfNeeded()
    }
}