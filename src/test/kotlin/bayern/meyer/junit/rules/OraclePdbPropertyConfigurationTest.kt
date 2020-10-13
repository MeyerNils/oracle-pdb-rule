package bayern.meyer.junit.rules

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OraclePdbPropertyConfigurationTest: AbstractConfigurationTest() {

    @Test
    fun testChangedSimpleProperties() {
        System.setProperty(PropertyKeys.CREATE_PDB, "false")
        System.setProperty(PropertyKeys.CREATE_PDB_EAGER, "true")
        System.setProperty(PropertyKeys.KEEP_PDB, "true")
        System.setProperty(PropertyKeys.GRANT_UNLIMITED_TABLESPACE, "true")
        System.setProperty(PropertyKeys.CDB_HOST, "host")
        System.setProperty(PropertyKeys.CDB_PORT, "port")
        System.setProperty(PropertyKeys.CDB_NAME, "name")
        System.setProperty(PropertyKeys.CDB_USERNAME, "propertyUsername")
        System.setProperty(PropertyKeys.CDB_PASSWORD, "propertyPassword")
        System.setProperty(PropertyKeys.ORADATA_FOLDER, "/oradata/")
        System.setProperty(PropertyKeys.PDB_BASE_PATH, "/pdbbase/")
        System.setProperty(PropertyKeys.PDBSEED_NAME, "pdbSeedName")
        val oraclePdbConfiguration = OraclePdbConfiguration.Builder()
                .withCdbHost("abc")
                .withCdbPort("abc")
                .withCdbName("abc")
                .withCdbUsername("abc")
                .withCdbPassword("abc")
                .withOradataFolder("abc")
                .withPdbBasePath("abc")
                .withPdbSeedName("abc")
                .build()
        assertEquals(false, oraclePdbConfiguration.createPdb)
        assertEquals(true, oraclePdbConfiguration.createPdbEager)
        assertEquals(true, oraclePdbConfiguration.keepPdb)
        assertEquals(true, oraclePdbConfiguration.grantUnlimitedTablespace)
        assertEquals("jdbc:oracle:thin:@host:port/name", oraclePdbConfiguration.cdbJdbcUrl)
        assertEquals("propertyUsername", oraclePdbConfiguration.cdbUsername)
        assertEquals("propertyPassword", oraclePdbConfiguration.cdbPassword)
        assertEquals("/oradata/name/pdbSeedName/", oraclePdbConfiguration.pdbSeedPath)
        assertEquals("/pdbbase/${oraclePdbConfiguration.pdbName}/", oraclePdbConfiguration.pdbPath)
    }

    @Test
    fun testChangedExtendedProperties() {
        System.setProperty(PropertyKeys.CDB_HOST, "host1")
        System.setProperty(PropertyKeys.CDB_PORT, "port1")
        System.setProperty(PropertyKeys.CDB_NAME, "name1")
        System.setProperty(PropertyKeys.CDB_JDBC_URL, "jdbc:oracle:thin:@host:port/name")
        System.setProperty(PropertyKeys.ORADATA_FOLDER, "/oradata/")
        System.setProperty(PropertyKeys.PDBSEED_NAME, "pdbSeedName")
        System.setProperty(PropertyKeys.PDB_SEED_PATH, "/my/pdb/seed/")
        val oraclePdbConfiguration = OraclePdbConfiguration.Builder()
                .withCdbJdbcUrl("abc")
                .withPdbBasePath("abc")
                .build()
        assertEquals("jdbc:oracle:thin:@host:port/name", oraclePdbConfiguration.cdbJdbcUrl)
        assertEquals("/my/pdb/seed/", oraclePdbConfiguration.pdbSeedPath)
    }
}