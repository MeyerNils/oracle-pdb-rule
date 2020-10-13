package bayern.meyer.junit.rules

import org.junit.Assert.*
import org.junit.Test

class OraclePdbConfigurationTest: AbstractConfigurationTest()  {

    @Test
    fun testDefaultValues() {
        val oraclePdbConfiguration = OraclePdbConfiguration.Builder().build()
        assertEquals(oraclePdbConfiguration.cdbJdbcUrl.substringBeforeLast("/") + "/"  + oraclePdbConfiguration.pdbName, oraclePdbConfiguration.pdbJdbcUrl)
        assertEquals("jdbc:oracle:thin:@localhost:1521/ORCLCDB", oraclePdbConfiguration.cdbJdbcUrl)
        assertEquals("sys as sysdba", oraclePdbConfiguration.cdbUsername)
        assertEquals("oracle", oraclePdbConfiguration.cdbPassword)
        assertEquals(true, oraclePdbConfiguration.createPdb)
        assertEquals(false, oraclePdbConfiguration.createPdbEager)
        assertEquals(false, oraclePdbConfiguration.keepPdb)
        assertEquals(false, oraclePdbConfiguration.grantUnlimitedTablespace)
        assertEquals("/opt/oracle/oradata/ORCLCDB/pdbseed/", oraclePdbConfiguration.pdbSeedPath)
        assertEquals("/opt/oracle/oradata/ORCLCDB/${oraclePdbConfiguration.pdbName}/", oraclePdbConfiguration.pdbPath)
    }

    @Test
    fun testUseCdb() {
        val oraclePdbConfiguration = OraclePdbConfiguration.Builder().withCdbUsername("cdbUser").withCdbPassword("cdbPassword").andDoNotCreatePdb().build()
        assertEquals(oraclePdbConfiguration.cdbJdbcUrl, oraclePdbConfiguration.pdbJdbcUrl)
        assertEquals(oraclePdbConfiguration.cdbUsername, oraclePdbConfiguration.pdbAdminUser)
        assertEquals(oraclePdbConfiguration.cdbPassword, oraclePdbConfiguration.pdbAdminPassword)
        assertEquals(false, oraclePdbConfiguration.createPdb)
    }

    @Test
    fun testChangedOradataFolder() {
        val oraclePdbConfiguration = OraclePdbConfiguration.Builder().withOradataFolder("/oradata/").build()
        assertEquals("/oradata/ORCLCDB/pdbseed/", oraclePdbConfiguration.pdbSeedPath)
        assertEquals("/oradata/ORCLCDB/${oraclePdbConfiguration.pdbName}/", oraclePdbConfiguration.pdbPath)
    }

    @Test
    fun testChangedPdbSeedName() {
        val oraclePdbConfiguration = OraclePdbConfiguration.Builder().withPdbSeedName("pdbSeedName").build()
        assertEquals("/opt/oracle/oradata/ORCLCDB/pdbSeedName/", oraclePdbConfiguration.pdbSeedPath)
    }

    @Test
    fun testChangedOradataPdbSeedAndPdbBaseFolder() {
        val oraclePdbConfiguration = OraclePdbConfiguration.Builder().withOradataFolder("/oradata/").withPdbBasePath("/pdbbase/").withPdbSeedPath("/pdbseed/").build()
        assertEquals("/pdbseed/", oraclePdbConfiguration.pdbSeedPath)
        assertEquals("/pdbbase/${oraclePdbConfiguration.pdbName}/", oraclePdbConfiguration.pdbPath)
    }

    @Test
    fun testConnectionSettings() {
        val oraclePdbConfiguration = OraclePdbConfiguration.Builder().withCdbHost("host").withCdbPort("port").withCdbName("name").build()
        assertEquals("jdbc:oracle:thin:@host:port/name", oraclePdbConfiguration.cdbJdbcUrl)
    }

    @Test
    fun testCdbJdbcUrl() {
        val oraclePdbConfiguration = OraclePdbConfiguration.Builder().withCdbJdbcUrl("jdbc:oracle:thin:@host:port/name").build()
        assertEquals("jdbc:oracle:thin:@host:port/name", oraclePdbConfiguration.cdbJdbcUrl)
    }

    @Test
    fun testBooleanSettings() {
        val oraclePdbConfiguration = OraclePdbConfiguration.Builder().grantUnlimitedTablespace().andDoNotCreatePdb().andCreatePdbEager().andKeepPdb().build()
        assertEquals(false, oraclePdbConfiguration.createPdb)
        assertEquals(true, oraclePdbConfiguration.createPdbEager)
        assertEquals(true, oraclePdbConfiguration.keepPdb)
        assertEquals(true, oraclePdbConfiguration.grantUnlimitedTablespace)
    }
}