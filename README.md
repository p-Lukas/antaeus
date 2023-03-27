
## Initial thoughts on how to tackle the problem:

### Task at hand:

- build a logic that will schedule payments and **trigger** payments via the PaymentProvider interface at the first of the month

### assumptions:
1. Time was spent investigating. Solutions like jobrunr.io and others were found to be inadequate for this task. That is why we write our own.
2. The PaymentProvider interface is external to this system, but internal to pleo itself (assumption was made due to passing customerId instead of IBANs).
3. There is only one PaymentProvider system.
4. It is unknown to us if and how fast the PaymentProvider interface can scale.
5. The number of invoices is not that large, that the machine will run out of memory when loading all at once.
6. The number of invoices is not that large, that one machine cannot process them in a timely manner.

### solution idea:
1. schedule task to be executed at the first of the month
2. fetch all unpaid and not failed invoices from the DB
3. process each fetched invoice:
   1. mark successfully charged invoices as paid
   2. mark invoices failed due to account balance as failed and notify customer 
   3. in case of a NetworkException the processing is throttled and failed ones retried
      - this is done because of assumption two, three and four
      - if the retry failed it will be marked for retry in the DB and retried with the other failed invoices 
   4. in case of a CustomerNotFoundException or CurrencyMismatchException, the invoice is marked as failed:
      - failed invoices will either be reviewed by the support-team or an automated solution exists to fix issues
      - failed invoices will be marked for retry
4. after support approves or an automated system fixed the failed invoices, they are marked by the external system ready for retry.
5. As retry marked invoices will be reprocessed. API-endpoint for trigger (/rest/v1/rerun). 


## Comments on possible Improvements:
- Not much thought put into the design of the added API-Endpoints. As they are considered out of scope for this task. Prob. Improvements needed.
- Batch processing of invoices could be implemented. Decreases loading time from DB and failover time improves.
- Process of handling failed Invoices 
- No recovery mechanism is implemented in case of processing failure.
- writing most testcases was skipped due to lack of time

## time spent:
1. 1,5h looking through the code base, running the rest API and sketching out the first solution idea
2. 3h first iteration: no concurrency, no tests, no scheduler
3. 2,5h Added Concurrency and scheduler. Investigation on how kotlin coroutine works in detail. Upgrading of dependencies to use this feature fully (most of the time spent here :/)
4. 1,5h Fighting with gradle a bit more. & added some test (should be expanded on...). + improved comments

### Total time spent:
~ 1d familiarising myself with Kotlin. It is the first time I use it. 
~ 1d working on this project

----

## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

Open the project using your favorite text editor. If you are using IntelliJ, you can open the `build.gradle.kts` file and it is gonna setup the project in the IDE for you.

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```

*Running through docker*

Install docker for your platform

```
docker build -t antaeus
docker run antaeus
```

### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── buildSrc
|  | gradle build scripts and project wide dependency declarations
|  └ src/main/kotlin/utils.kt 
|      Dependencies
|
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database 
|       models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the Internal and API models used throughout the
|       application.
|
└── pleo-antaeus-rest
        Entry point for HTTP REST API. This is where the routes are defined.
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!
