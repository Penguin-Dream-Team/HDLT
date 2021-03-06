# Highly Dependable Location Tracker - Stage 1

## Environment & Technologies

The project was developed in [Kotlin](https://kotlinlang.org) and uses [Gradle](https://gradle.com) as a build tool to
facilitate development as well as testing. We recommend opening the entire project in [IntelliJ](https://www.jetbrains.com/idea/) as it comes with gradle
and kotlin installed. It is a lot easier to work within this IDE. It integrates fairly well with both of these
technologies.

## Modules Structure

The project is divided in 5 different modules:

- **HDLT-CA** - An helper to generate all the certificates and private keys
- **HDLT-MasterServer** - The client that helps visualize the user grid, as well as control the epochs broadcasts. It
  aids in setting up the entire system
- **HDLT-LocationServer** - The server that handles all the users' connections as well as persist the epochs' data.
- **HDLT-User** - The user can request proofs from other users and submit the reports (with proofs) to the server. They
  can also query their own submitted reports.
- **HDLT-HA** - The HA can query all the reports from all users on the server

## GRPC - Protos

The communication is done using GRPC. The proto files are inside the `protos` module. They can be compiled using
the `generateProto` gradle task from the same module.

## Server - Database

To persist data in the server, we decided to use a simple SQLite database. It stores the user reports and their proofs.
There is also a set of tables for the nonces (to ensure freshness). To create the database and run the migrations, you
can use the `migrateDb` gradle task.

## Running everything

To run the system, you first start the LocationServer followed by the MasterServer. When the MasterServer launches, you
will see a window that will help to set up the system. Here you can select the amount of users, grid size (columns and
rows), f and f' parameters. To run each of these modules, use the following commands:

```bash
gradlew HDLT-MasterServer:run
```

After setting up the master, you need to fire up the servers. There should be as many servers as you defined in the master.
To run a server use the following command:

```bash
gradlew HDLT-LocationServer:run --args="<id> <byzantineLevel>"
```

After setting up the master, you need to fire up the users. There should be as many users as you defined in the master.
To run a user use the following command:

```bash
gradlew HDLT-User:run --args="<id> <serverAddress> <port> <byzantineLevel>"
```

The id should be incremental for each user and should start at 0. The serverAddress should be `localhost` in most cases.
The port of the server is hardcoded as `7777` for now. Byzantine level defines if the user will be attempting to be
byzantine or not (-1 for always correct, 5 for hardest level).

Byzantine levels:

0. Forge reports with _self-signed_ proofs
1. Skip epoch communication
2. Tamper some fields in requests
3. Reject another user's requests
4. Redirect requests to other users
5. No information verification

_**NOTE:** -1 for no byzantine_

_**NOTE:** The users' ports start at 8100, and the id is used to get a unique port per user: 8100+id. This is why the id
needs to be incremental and start at 0_

The master will define the byzantine users as the last f users. This means that if you wish to have byzantine users,
make sure their id starts from userCount - f.

To run the HA you can execute the following command:

```bash
gradlew HDLT-HA:run --args="<serverAddress> <port>"
```

If you wish to create new certificates, you can use the `generate` script with the CA. The command to run is:

```bash
gradlew HDLT-HA:run < generate
```

If you want to change the values, you can run the command without the script, but change the values also in the respective
`Main.kt` of the module, and replace the generated keystores in the respective folders (`src/main/resources` of the module).
Default values are simple just for simple debug, but passwords can be strengthened very easily.

# Full example

## Example One

We will be using 3 LocationServer, 1 MasterServer, 3 Users and 1 HA for this example. The grid will be 4 rows by 4
columns, with a random seed 0 and we will have a maximum of 0 byzantine users and servers. You will need 8 different terminal windows to run this setup.

Let us start by firing up 3 LocationServer. Each server will have its own terminal, and the series of commands will be the following:

```bash
gradlew HDLT-LocationServer:run --args="0 -1"
gradlew HDLT-LocationServer:run --args="1 -1"
gradlew HDLT-LocationServer:run --args="2 -1"
```

Now we need to start all the users. Each user will have its own terminal, and the series of commands will be the following:

```bash
gradlew HDLT-User:run --args="0 localhost 7777 -1"
gradlew HDLT-User:run --args="1 localhost 7777 -1"
gradlew HDLT-User:run --args="2 localhost 7777 -1"
```

We also start the HA:

```bash
gradlew HDLT-HA:run --args="localhost 7777"
```

Finally we can start the MasterServer in another terminal:

```bash
gradlew HDLT-MasterServer:run
```

The setup window should be populated to look like this:

![masterViewSetup](images/masterViewSetup1.png)

You can press `Finish Setup` and start!

You can now see the users move. Other terminals will also
log the requests being received and sent. At any point, a user can request the server for proofs of a specific epoch.
One can do this by just typing the desired epoch in the user prompt. If the user happens to be byzantine, he can try to
fetch the reports of another user by specifying the other user's id after the epoch.


## Example Two

We will be using 4 LocationServer, 1 MasterServer, 3 Users and 1 HA for this example. The grid will be 4 rows by 4
columns, with a random seed 0 and we will have a maximum of 0 byzantine users and 1 byzantine server. You will need 9 different terminal windows to run this setup.

Let us start by firing up 4 LocationServer. Each server will have its own terminal, and the series of commands will be the following:

```bash
gradlew HDLT-LocationServer:run --args="0 -1"
gradlew HDLT-LocationServer:run --args="1 -1"
gradlew HDLT-LocationServer:run --args="2 -1"
gradlew HDLT-LocationServer:run --args="3 1"
```

Remember to place the byzantine in last place (with the higher id), since the MasterServer will treat that server as the byzantine one.

Now we need to start all the users. Each user will have its own terminal, and the series of commands will be the following:

```bash
gradlew HDLT-User:run --args="0 localhost 7777 -1"
gradlew HDLT-User:run --args="1 localhost 7777 -1"
gradlew HDLT-User:run --args="2 localhost 7777 -1"
```

We also start the HA:

```bash
gradlew HDLT-HA:run --args="localhost 7777"
```

Finally we can start the MasterServer in another terminal:

```bash
gradlew HDLT-MasterServer:run
```

The setup window should be populated to look like this:

![masterViewSetup](images/masterViewSetup2.png)

You can press `Finish Setup` and start!

In this example we can see what happens when a Byzantine server decides to drop a request. The user will receive an Empty Response from the server since the quorum was not met.
With the random seed provided, this will happen on the Epoch 2. 
You can check both the user and the server logs to understand what happened.


## Example Three

We will be using 4 LocationServer, 1 MasterServer, 4 Users and 1 HA for this example. The grid will be 4 rows by 4
columns, with a random seed 0 and we will have a maximum of 2 byzantine users and 1 byzantine server. You will need 10 different terminal windows to run this setup.

Let us start by firing up 4 LocationServer. Each server will have its own terminal, and the series of commands will be the following:

```bash
gradlew HDLT-LocationServer:run --args="0 -1"
gradlew HDLT-LocationServer:run --args="1 -1"
gradlew HDLT-LocationServer:run --args="2 -1"
gradlew HDLT-LocationServer:run --args="3 1"
```

Remember to place the byzantine in last place (with the higher id), since the MasterServer will treat that server as the byzantine one.

Now we need to start all the users. Each user will have its own terminal, and the series of commands will be the following:

```bash
gradlew HDLT-User:run --args="0 localhost 7777 -1"
gradlew HDLT-User:run --args="1 localhost 7777 -1"
gradlew HDLT-User:run --args="2 localhost 7777 1"
gradlew HDLT-User:run --args="3 localhost 7777 1"
```

Once again, the byzantine must be in the last place (with the higher id), since the MasterServer will treat that user as the byzantine one.


We also start the HA:

```bash
gradlew HDLT-HA:run --args="localhost 7777"
```

Finally we can start the MasterServer in another terminal:

```bash
gradlew HDLT-MasterServer:run
```

The setup window should be populated to look like this:

![masterViewSetup](images/masterViewSetup3.png)

You can press `Finish Setup` and start!

In this example we can see what happens when a Byzantine user decides to tampper a request. The user will receive an Invalid Request detected by the servers since the contents of the report were changed.
With the random seed provided, this will happen on the 29. 
You can check both the user and the server logs to understand what happened.
