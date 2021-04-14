# HDLT-LocationServer

## Setup

### Database Setup

The server uses a database connection to the reports database.

### Migrate Database Schema Files

The API is using [flyway](https://flyway.org/) to migrate the database schemas. This will allow the application to
automatically create the tables and populate them accordingly. The files for the migrations can be found in
the `resources/db/migrations/` folder and need to be prepended with the version in the format
of `V{#}__migration_name.sql`. To migrate the files to the database, you can use the following gradle task:

```bash
flywayMigrate
```

This should create the `database.sqlite` file under `resources/db/`. After running the migrations you will need to
regenerate the Database files as in the [Database files](#generate-database-files)

### Generate Database Files

The server is using [jooq](https://www.jooq.org/) to create database queries, which requires scanning the databases used
to generate a typesafe schema. The schema is committed to the repository, but in case any new tables are created and
they are required in the server, they can be generated using the following gradle task:

```bash
generateDatabaseJooq
```

For this to work, gradle needs to know the database credentials for the database server that is being scanned. The
database needs to exist.

### Database Full Setup

If you want to run both the migrations and generate the resulting files you can use the following gradle task:

```bash
migrateDb
```

### Database Clean

If you wish to clean the entire database (drop the tables), you can use the following gradle task:

```bash
cleanDb
```

### Database Reset

If you wish to clean the entire database (drop the tables) and recreate them, you can use the following gradle task:

```bash
resetDb
```

## Running

To run the server after setting it up completely, simply run the gradle task `run` and the users should be able to
access it on port 7777.