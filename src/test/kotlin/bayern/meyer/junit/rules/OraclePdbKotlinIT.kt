package bayern.meyer.junit.rules

import org.junit.ClassRule
import org.junit.Test

class OraclePdbKotlinIT {
    @Test
    fun withoutFurtherConfigurationTest() {
        oraclePdbWithoutConfiguration.pdbDataSource.connection.use { connection -> connection.createStatement().use { statement -> statement.execute("SELECT 1 FROM DUAL") } }
    }

    companion object {
        @ClassRule
        @JvmField
        val oraclePdbWithoutConfiguration = OraclePdb()
    }
}