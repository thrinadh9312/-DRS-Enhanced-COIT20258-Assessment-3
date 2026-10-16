# DRS-Enhanced — COIT20258 Assessment 3

Team Name: T9

Team Leader Name: Thirthala Thrinadh (12313659)
Team Member 1:    Harsha Vardhan Rayi (12307204)
Team Member 2:    Teja Annam (12315178)



## Project overview

DRS-Enhanced is a Java Maven project that can be opened, built and run
using Apache NetBeans.

The application has three tiers:

1. JavaFX client interface.
2. Multithreaded Java HTTPS server containing the business logic.
3. MySQL database for persistent storage.

The application supports disaster reporting, assessment, prioritisation,
department and resource allocation, incident monitoring, search,
text-report export and account administration.

The two additional features are:

- Evacuation shelter management with capacity checks.
- Incident alerts with user acknowledgements.

## 1. Required software

Install:

- Apache NetBeans with Java and Maven project support.
- JDK 17.
- MySQL Server 8.x.
- MySQL Workbench, optionally, for inspecting database tables.

The bundled Windows JavaFX libraries require a Windows x64 JDK.

On Windows 11 ARM64, use a Windows x64 JDK for this project so the Java
runtime matches the bundled JavaFX libraries. Selecting an ARM64 JDK
with these x64 libraries causes an architecture mismatch.

MySQL Workbench alone is not the database server. Ensure MySQL Server
is installed and running.

Maven needs an internet connection when downloading dependencies and
plugins for the first build.

## 2. Open the project in NetBeans

1. Extract the ZIP completely into a writable folder.
2. Open NetBeans.
3. Select File > Open Project.
4. Select the DRS-Enhanced folder containing pom.xml.
5. Click Open Project.
6. Wait for NetBeans to recognise the Maven project and resolve its
   dependencies.

Open the project folder itself, not the src or dist subfolder.

## 3. Select the correct Java platform

1. In NetBeans, open Tools > Java Platforms.
2. Click Add Platform if the required JDK is not listed.
3. Select the installed Windows x64 JDK 17 folder.
4. Finish adding the platform.
5. Right-click the DRS project and select Properties.
6. Find the Java Platform setting, usually under Build > Compile.
7. Select the Windows x64 JDK 17 platform.
8. Click OK.

The exact menu arrangement may vary between NetBeans versions.

The JDK used to run NetBeans itself may differ from the JDK selected
for this project.

## 4. Build and test the project

1. Right-click the project.
2. Select Clean and Build.
3. Wait for the build and automated tests to finish.
4. Check the Output window.

A successful run should show:

    Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
    BUILD SUCCESS

The build creates:

    target/drs-enhanced-1.0.0.jar
    target/lib/
    target/surefire-reports/

The compiler may report unchecked operations in MainController.java.
This is a warning and does not itself mean that the build failed.

Save a screenshot of the successful build and test results for the report.

## 5. Configure the terminal Java installation

The server runs separately from the NetBeans client.

Open Command Prompt in the project folder. Ensure that the terminal
also uses the intended JDK.

If necessary, run the following commands, replacing the example path
with your actual Windows x64 JDK 17 installation folder:

```bat
set "JAVA_HOME=C:\Path\To\Your\Windows-x64-JDK-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"
```

Check the selected Java:

```bat
java -XshowSettings:properties -version 2>&1 | findstr "java.home os.arch"
```

For the bundled Windows JavaFX libraries, os.arch should show amd64.

These commands change Java selection only for the current terminal.

## 6. First-time configuration

Perform this section only for a new installation.

If the application already works and config/server.properties exists,
keep the existing configuration and skip to Section 7.

From Command Prompt in the project folder, run:

```bat
run.bat setup
```

Setup generates:

- config/server.properties
- config/client.properties
- config/server.p12
- config/client-trust.p12
- config/server.cer

Open config/server.properties in a text editor.

Set these values to your local MySQL account:

```properties
DRS_DB_USER=root
DRS_DB_PASSWORD=local-mysql-password
```

Use the actual database username if it is different from root.

The default database settings are:

```properties
DRS_DB_NAME=drs_enhanced
DRS_DB_BASE=jdbc:mysql://localhost:3306/
```

Preserve the generated values for:

- DRS_DATA_KEY
- DRS_TLS_PASSWORD
- DRS_ADMIN_PASSWORD

The database account must have permission to create the database and
tables during initial startup.

Java properties files use backslash as an escape character. If a
password contains a literal backslash, write it as two backslashes.

Do not regenerate the data key when reusing an existing database:
the original key is needed to decrypt previously saved information.

## 7. Start the MySQL database and DRS server

1. Ensure MySQL Server is running.
2. Open Command Prompt in the project folder.
3. If you rebuilt or changed the application, close any running client
   and server before copying the newly built JAR:

```bat
copy /Y "target\drs-enhanced-1.0.0.jar" "dist\drs-enhanced-1.0.0.jar"
```

This keeps the server's bundled executable consistent with the source
that NetBeans runs.

If Maven dependencies were changed, the bundled runtime libraries
must also be updated to match the new dependencies.

4. Start the server:

```bat
run.bat server
```

5. Wait for:

    DRS-Enhanced HTTPS server ready

Keep this terminal open while using the application.

The server creates the database and tables programmatically when needed.

If the server is already running successfully, do not start a second
server on the same port.

## 8. Run the client from NetBeans

1. Return to NetBeans.
2. Right-click the DRS project.
3. Select Run.
4. Wait for the JavaFX login screen.

The included nbactions.xml maps Run Project to the Maven javafx:run goal.

The client connects to:

    https://localhost:8443

The project folder must remain the working directory so the client
can locate its configuration and certificate trust store.

For this workflow:

- NetBeans runs the JavaFX client.
- Command Prompt runs the DRS server.
- MySQL Server runs as the database service.

## 9. Sign in

For the initial administrator account:

    Username: admin

The password is the generated value after DRS_ADMIN_PASSWORD= in:

    config/server.properties

Enter the password value only, without the property name.

This application password is separate from the MySQL password.

Changing DRS_ADMIN_PASSWORD in the configuration file does not reset
an administrator account already created in the database.

Additional users are created through the User accounts tab.

## 10. Basic application workflow

### Create user accounts

1. Sign in as admin.
2. Open User accounts.
3. Enter a username and a password of at least 12 characters.
4. Select a role.
5. Select a department for RESPONDER accounts.
6. Click Create account.

Available roles:

- ADMIN
- COORDINATOR
- REPORTER
- RESPONDER

### Report and coordinate an incident

1. Open Report disaster.
2. Enter the disaster details and a valid date/time.
3. Click Submit incident.
4. Open Incidents and note the generated ID.
5. As a coordinator or administrator, select the incident.
6. Save its assessment.
7. Allocate primary and supporting departments and resources.
8. Update response progress as appropriate.

The normal status sequence is:

    Reported > Assessed > Assigned > In Progress > Resolved > Closed

Only a coordinator or administrator can close an incident.
Active evacuation allocations must be released before closure.

### Manage evacuation shelters

1. Open Evacuation shelters.
2. Create a shelter with a positive capacity.
3. Select the shelter in the upper table.
4. Enter an open incident ID and the number of people.
5. Click Allocate evacuation.
6. To restore capacity, select the allocation in the lower table and
   click Release selected evacuation.

Allocations exceeding remaining capacity are rejected.

### Publish and acknowledge alerts

1. As a coordinator or administrator, open Alerts.
2. Enter an incident ID and a message.
3. Click Publish alert.
4. Sign in as an authorised user for that incident.
5. Select the alert and click Acknowledge selected alert.

Reporters can see alerts associated with their own incidents.
Responders can see alerts for incidents assigned to their department.

Alerts are refreshed manually using Incidents > Refresh all.
Repeated acknowledgement by the same user does not create a duplicate.

Use the same incident ID throughout a connected test scenario.

### Search and export

1. Open Incidents.
2. Enter search text to filter the loaded records.
3. Clear the search box to restore the list.
4. Select an incident.
5. Click Export selected report.
6. Save and open the text report.

## 11. Import data from the initial prototype

The provided initial source project stores incidents in memory and
contains no persisted incident dataset.

For demonstration:

1. Sign in as a coordinator or administrator.
2. Open Import initial data.
3. Click Choose JSON file and import.
4. Select data/sample-incidents.json.

The sample file contains demonstration records.

Repeated imports skip records with existing legacy IDs.

If actual Assessment 2 text exports are available, convert them with
Python 3:

```text
python scripts/convert_initial_exports.py path/to/exports data/initial-import.json
```

Import the resulting JSON through the client.

The converter expects the text-report format produced by DRS-Initial.
It is not intended to convert DRS-Enhanced text exports.

Do not present demonstration records as recovered Assessment 2 data.

## 12. SQL scripts

The server automatically creates its database and tables.

The following scripts are also supplied for manual setup:

    sql/schema.sql
    sql/create-and-populate.sql

From the project folder, the MySQL command-line client can run:

```bat
mysql -u root -p < sql/create-and-populate.sql
```

This creates the schema and inserts demonstration shelter records.

Incident data is imported through the application server so encrypted
fields and audit records are generated correctly.

The schema file inside src/main/resources/db/ is required by the server.
Keep it even though a separate manual SQL copy exists.

## 13. Testing and evidence

Automated tests:

- ServiceTest: 28 tests.
- TransportTest: 3 tests.

Run them using NetBeans Clean and Build.

Alternatively, run the bundled tests from Command Prompt:

```bat
test.bat
```

Manual MySQL testing should also verify:

- Valid and invalid login.
- Role-based access.
- Reporting and input validation.
- Assessment and allocation.
- Status transitions and closure.
- Search and export.
- Shelter capacity and release.
- Alerts and acknowledgements.
- Import and duplicate handling.
- Account enabling and disabling.
- Audit records.
- Persistence after restart.
- Conflicting updates from two clients.


See docs/RUN_GUIDE.html for interface illustrations.

## 14. Troubleshooting

### ARM64 / AMD64 DLL error

The Java runtime and JavaFX native libraries have different architectures.

Select a Windows x64 JDK for the client and check the project Java
platform in NetBeans.

### Connection refused

Check that MySQL and the DRS server are running.
The default ports are 3306 for MySQL and 8443 for DRS.

### MySQL access denied

Check DRS_DB_USER, DRS_DB_PASSWORD and database permissions.

### TLS or certificate error

Use the matching client.properties and client-trust.p12 files generated
during setup. Connect using localhost for the bundled local certificate.

### Incident changed

Another client saved the incident first.
Click Refresh all, select the incident again and retry.

### Login rejected after editing the configuration password

The administrator account already exists in the database.
Editing the bootstrap password does not change that account's password.

### Port already in use

Check whether a DRS server is already running.
Use the existing server or stop it before starting another instance.

## 15. Stop the application

1. Sign out and close the JavaFX client.
2. Return to the server terminal.
3. Press Ctrl+C to stop the server.

Saved records remain in MySQL.

Do not delete the local configuration or encryption key.

## 16. Submission packaging

Keep:

- Source code and test source.
- pom.xml and nbactions.xml.
- Executable JAR and required libraries in dist/.
- JavaFX resources and FXML.
- SQL scripts.
- Import sample and conversion script.
- Run/build/test scripts.
- README and third-party notices.
- Required design and testing documentation.

The generated target/ folder can be excluded from the submission ZIP
because NetBeans recreates it. Preserve any required test evidence first.

Keep private configuration files on the development computer, but do
not include passwords, encryption keys or server private keys in the
submission copy. A fresh installation generates its own configuration
using run.bat setup.

The code package must be accompanied by the required group report,
actual testing screenshots and GitHub evidence.

Each student must submit the individual teamwork report separately.
