# oracle-pdb-rule
[![Build Status](https://travis-ci.org/MeyerNils/oracle-pdb-rule.svg?branch=main)](https://travis-ci.org/MeyerNils/oracle-pdb-rule)
[![Maven Central](https://img.shields.io/maven-central/v/bayern.meyer/oracle-pdb-rule.svg?label=Maven%20Central)](http://search.maven.org/#search%7Cga%7C1%7Cbayern.meyer)
[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/MeyerNils/oracle-pdb-rule.svg?label=License)](http://www.apache.org/licenses/)

This Maven module is intended to ease the provisioning of an Oracle test database for integration or system tests based on JUnit. 

It makes use of Oracles [Pluggable Database Feature](https://docs.oracle.com/database/121/CNCPT/cdbovrvw.htm) to create a new pluggable database (PDB) for JUnit tests and remove it again when the JVM shuts down. This approach is interesting, as other approaches like spinning up a database using Docker and the [Testcontainers](https://www.testcontainers.org/) framework, that work well for other databases like Postgres, does not scale for Oracle due to its high resource requirements and long startup and initialization times (> 10 minutes) for provided [Oracle Database Docker Images](https://github.com/oracle/docker-images/tree/master/OracleDatabase). 

The provisioning is plugged into the test execution using a [JUnit Rule](https://github.com/junit-team/junit4/wiki/Rules). Each instance of the class `bayern.meyer.junit.rules.OraclePdb` creates a PDB with a random name once when the rule is first triggered. The PDB is kept until JVM gracefully shuts down. It's removed using a JVM shutdown hook by this module than. Generated PDB name, username and password are provided by corresponding getters of this rule class. 

Tools like [Flyway](https://flywaydb.org/), [Liquibase](https://www.liquibase.org/) or custom scripts can be used to run DDL scripts within this empty PDB, tools like [DbUnit](http://dbunit.sourceforge.net/), [DbSetup](http://dbsetup.ninja-squad.com/) or other ways to initialize the test data. 

An Oracle container database (CDB) being capable of provisioning PDBs can e.g. be provisioned once using the [Oracle Database Docker Image preparation scripts for Oracle 19c](https://github.com/oracle/docker-images/tree/master/OracleDatabase/SingleInstance).  


## Usage
* [Maven dependency](#maven-dependency)
* [Declaring the test rule](#declaring-the-test-rule)
* [Accessing the database](#accessing-the-database)
* [Configuring the CDB access](#configuring-the-cdb-access)
* [Sharing a database between tests](#sharing-a-database-between-tests)
* [Troubleshooting](#troubleshooting)
* [Background information](#background-information)

### Maven dependency
Add this module as dependency to your project
```xml
<dependency>
    <groupId>bayern.meyer</groupId>
    <artifactId>oracle-pdb-rule</artifactId>
    <version>0.3</version>
    <scope>test</scope>
</dependency>
```
### Declaring the test rule
To create a new pluggable database before running tests add a `@ClassRule`
```java
public class AnIntegrationTest {
    @ClassRule
    public static OraclePdb oraclePdb = new OraclePdb();

    @Test
    public void aTest() { /*...*/ }
}
```
### Accessing the database
The provisioned pluggable database can be accessed using provided connection URL, username and password.
```java
public class AnIntegrationTest {
    @ClassRule
    public static OraclePdb oraclePdb = new OraclePdb();

    @BeforeClass
    public static void prepareDatabaseServer() throws SQLException {
        OracleDataSource pdbAdminDataSource = new OracleDataSource();
        pdbAdminDataSource.setURL(oraclePdb.getPdbJdbcUrl());
        pdbAdminDataSource.setUser(oraclePdb.getPdbAdminUser());
        pdbAdminDataSource.setPassword(oraclePdb.getPdbAdminPassword());

        try (Connection connection = pdbAdminDataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("...");
        }
    }

    @Test
    public void aTest() { /*...*/ }
}
```
Besides a `OracleDataSource` can be obtained directly.
```java
public class AnIntegrationTest {
    @ClassRule
    public static OraclePdb oraclePdb = new OraclePdb();

    @BeforeClass
    public static void prepareDatabaseServer() throws SQLException {
        final OracleDataSource pdbAdminDataSource = oraclePdb.getPdbDataSource();

        try (Connection connection = pdbAdminDataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("...");
        }
    }

    @Test
    public void aTest() { /*...*/ }
}
```
### Configuring the CDB access
The CDB access can be configured programmatically. See JavaDoc for public methods for more details.
```java
public class AnIntegrationTest {
    @ClassRule
    public static OraclePdb oraclePdb = new OraclePdb(new OraclePdbConfiguration.Builder().withCdbJdbcUrl("jdbc:oracle:thin:@localhost:1521/ORCLCDB").withCdbUsername("sys as sysdba").withCdbPassword("oracle").build());

    @Test
    public void aTest() { /*...*/ }
}
```
Besides CDB access and other setting can be configured using system properties. If both, programmatic and system properties are configured the system properties take precedence. 
```bash
mvn -DCDB_PASSWORD=MySecretPassword test
```
Following properties can be configured using `-D`
* **CDB_USERNAME** (sys as sysdba)
* **CDB_PASSWORD** (oracle)
* **CDB_HOST** (localhost)
* **CDB_PORT** (1521)
* **CDB_NAME** (ORCLCDB)
* **CDB_DOMAIN** () - suffix to add to the CDB name to build the jdbc connection URL
* **CDB_JDBC_URL** (jdbc:oracle:thin:@${CDB_HOST}:${CDB_PORT}/${CDB_NAME}${CDB_DOMAIN})
* **ORADATA_FOLDER** (/opt/oracle/oradata)
* **PDBSEED_NAME** (pdbseed)
* **PDB_SEED_PATH** (${ORADATA}/${CDBNAME}/${PDBSEED_NAME}/)
* **PDB_BASE_PATH** (${ORADATA}/${CDBNAME}/)
* **CREATE_PDB** (`true`) - Use this property to skip PDB creation and use the database credentials provided for the CDB access to access the test database. This mode can be helpful to test locally without spinning up a new database over and over again. 
* **KEEP_PDB** (`false`) - Use this property to skip PDB deletion after the tests have finished. This mode can be helpful to locally verify the database content after tests have been executed.
* **CREATE_PDB_EAGER** (`false`) - Set this property to `true` to create the PDB already then the OraclePdb object gets created; otherwise the PDB will be created in the test rules `apply` method just before the test execution
* **GRANT_UNLIMITED_TABLESPACE** (`false`) - Set this property to `true` to execute `GRANT UNLIMITED TABLESPACE TO <PDB_ADMIN_USER>` right after PDB creation
### Sharing a database between tests
A new pluggable database is created for every `@Rule` or `@ClassRule` referenced instance of class `bayern.meyer.junit.rules.OraclePdb`. To share a database a shared instance of the rule class can be used.
```java
public class PdbProvider {
    public static OraclePdb oraclePdb = new OraclePdb();
}

public class AnIntegrationTest {
    @ClassRule
    public static OraclePdb oraclePdb = PdbProvider.oraclePdb;

    @Test
    public void aTest() { /*...*/ }
}

public class AnotherIntegrationTest {
    @ClassRule
    public static OraclePdb oraclePdb = PdbProvider.oraclePdb;

    @Test
    public void anotherTest() { /*...*/ }
}
```
### Troubleshooting
SLF is used as logging API. Helpful information is logged on info and debug level using the logger `bayern.meyer.junit.rules.OraclePdb`.
### Background information
Following SQL statements are executed for creating a pluggable database and determining its service name
```sql
ALTER SESSION SET CONTAINER = CDB$ROOT
CREATE PLUGGABLE DATABASE ${pdbName} ADMIN USER ${pdbAdminUser} IDENTIFIED BY ${pdbAdminPassword} ROLES=(DBA) FILE_NAME_CONVERT=('${pdbSeedPath}','${pdbPath}')
ALTER PLUGGABLE DATABASE ${pdbName} OPEN
ALTER SESSION SET CONTAINER = ${pdbName}
SELECT sys_context('userenv','service_name') FROM dual
```
Following SQL statement is executed to grant unlimited tablespace (if `GRANT_UNLIMITED_TABLESPACE` is set to `true`)
```sql
GRANT UNLIMITED TABLESPACE TO ${pdbAdminUser}
```
Following SQL statements are executed for removing a pluggable database
```sql
ALTER SESSION SET CONTAINER = CDB$ROOT
ALTER PLUGGABLE DATABASE ${pdbName} CLOSE IMMEDIATE
DROP PLUGGABLE DATABASE ${pdbName} INCLUDING DATAFILES
```