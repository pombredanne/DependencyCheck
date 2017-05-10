Tasks
====================

Task                                             | Description
-------------------------------------------------|-----------------------
[dependencyCheck](configuration.html)            | Runs dependency-check against the project and generates a report.
dependencyCheckUpdate                            | Updates the local cache of the NVD data from NIST.
[dependencyCheckPurge](configuration-purge.html) | Deletes the local copy of the NVD. This is used to force a refresh of the data.

Configuration: dependencyCheckUpdate
====================
The following properties can be configured for the dependencyCheckUpdate task:

Property             | Description                        | Default Value
---------------------|------------------------------------|------------------
cveValidForHours     | Sets the number of hours to wait before checking for new updates from the NVD.                                     | 4
failOnError          | Fails the build if an error occurs during the dependency-check analysis.                                           | true

#### Example
```groovy
dependencyCheckUpdate {
    cveValidForHours=1
}
```

### Proxy Configuration

Property          | Description                        | Default Value
------------------|------------------------------------|------------------
server            | The proxy server.                  | &nbsp;
port              | The proxy port.                    | &nbsp;
username          | Defines the proxy user name.       | &nbsp;
password          | Defines the proxy password.        | &nbsp;
connectionTimeout | The URL Connection Timeout.        | &nbsp;

#### Example
```groovy
dependencyCheckUpdate {
    proxy {
        server=some.proxy.server
        port=8989
    }
}
```

### Advanced Configuration

The following properties can be configured in the dependencyCheck task. However, they are less frequently changed. One exception
may be the cvedUrl properties, which can be used to host a mirror of the NVD within an enterprise environment.
Note, if ANY of the cve configuration group are set - they should all be set to ensure things work as expected.

Config Group | Property          | Description                                                                                 | Default Value
-------------|-------------------|---------------------------------------------------------------------------------------------|------------------
cve          | url12Modified     | URL for the modified CVE 1.2.                                                               | https://nvd.nist.gov/download/nvdcve-Modified.xml.gz
cve          | url20Modified     | URL for the modified CVE 2.0.                                                               | https://nvd.nist.gov/feeds/xml/cve/nvdcve-2.0-Modified.xml.gz
cve          | url12Base         | Base URL for each year's CVE 1.2, the %d will be replaced with the year.                    | https://nvd.nist.gov/download/nvdcve-%d.xml.gz
cve          | url20Base         | Base URL for each year's CVE 2.0, the %d will be replaced with the year.                    | https://nvd.nist.gov/feeds/xml/cve/nvdcve-2.0-%d.xml.gz
data         | directory         | Sets the data directory to hold SQL CVEs contents. This should generally not be changed.    | &nbsp;
data         | driver            | The name of the database driver. Example: org.h2.Driver.                                    | &nbsp;
data         | driverPath        | The path to the database driver JAR file; only used if the driver is not in the class path. | &nbsp;
data         | connectionString  | The connection string used to connect to the database.                                      | &nbsp;
data         | username          | The username used when connecting to the database.                                          | &nbsp;
data         | password          | The password used when connecting to the database.                                          | &nbsp;

#### Example
```groovy
dependencyCheckUpdate {
    data {
        directory='d:/nvd'
    }
}
```
