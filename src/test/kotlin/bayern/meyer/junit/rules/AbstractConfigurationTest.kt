package bayern.meyer.junit.rules

import org.junit.After
import org.junit.Before

abstract class AbstractConfigurationTest {
    private val properties = mutableMapOf<String, String>()

    @Before
    fun clearProperties() {
        PropertyKeys.javaClass.declaredFields.filter { it.type == String::class.java }.map { it.get(PropertyKeys) as String }.forEach { key ->
            System.getProperty(key)?.let { value ->
                properties[key] = value
                System.clearProperty(key)
            }
        }
    }

    @After
    fun resetProperties() {
        properties.forEach {
            System.setProperty(it.key, it.value)
        }
    }

}
