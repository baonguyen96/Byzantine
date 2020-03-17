# Byzantine

## Introduction

**TO BE WRITTEN**

## Requirements

1. Java 8 or higher
2. JDK 8 or higher
3. JRE 1.8 or higher
4. Maven
5. Windows 10 and/or Ubuntu LTS 18.4 (tested)

## How to run

### Build

1. Run the following command from the [root](#) directory:
```
mvn clean install -U
mvn package
```

2. A jar file should be generated under `Server/target/` and `Client/target` directories

### Run

#### Interactively

1. Start multiple instances of Server either from IDE or by running command `java -jar Name.jar` where `Name` is the rest of the jar file name in the `Server/target/` directory, then populate all necessary fields on them as prompted, but DO NOT choose to start any instance until all server instances are ready to be activated
2. Activate (choose start) all Server instances at the same time
3. Start multiple instances of Client either from IDE or by running command `java -jar Name.jar` where `Name` is the rest of the jar file name in the `Client/target/` directory, populate all necessary fields on them as prompted, but DO NOT choose to start any instance until all server instances are ready to be activated
4. Activate (choose start) all Client instances at the same time

#### Statically

1. Create configuration file for each `Server` instance following [this format](./Server/src/main/resources/Configurations/ServerConfiguration.txt) with: line 1 as the file directory; line 2 as server's IP name, address, and port number; line 3 as list of other servers' names, IP addresses, and ports separated by pipe
2. Create configuration file for each `Client` instance following [this format](./Client/src/main/resources/Configurations/ClientConfiguration.txt) with: line 1 as the client name; line 2 as list of other servers' names, IP addresses, and ports separated by pipe
3. Run `java -jar Name.jar Path` where `Name` is the rest of the jar file name in the `Server/target/` directory and `Path` is the full path to the server's configuration file created above at the same time
4. Run `java -jar Name.jar Path` where `Name` is the rest of the jar file name in the `Client/target/` directory and `Path` is the full path to the client's configuration file created above at the same time

#### Local Simulator (Windows only)

A set of automated PowerShell scripts are provided to automatically run all servers and clients on a local machine to test. Follow these steps to run:

1. Go to [Scripts](./Scripts) directory using PowerShell
2. Run `./Build-Local.ps1` to build the entire project
3. Run `./Reset-Local-Server.ps1` to reset all directories of all servers
4. Run `./Start-Local-Server.ps1` to start all (3) server instances
5. Run `./Start-Local-Client.ps1` to start all (5) client instances

## Project Documentation

See [Documentation](./Documentation/Documentation.md) for more explanation on project design, architecture, and proof of correctness.
