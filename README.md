# Travel Planner

A simple trip planning application built with Java, Spring Boot, Maven, and MySQL.

## Features

- Create an account and sign in.
- Create trips with dates, transport, and a budget.
- Add daily activities to an itinerary.
- Invite friends to a trip and split expenses equally.
- Make a trip private or share a read-only public link.
- Check destination weather and open routes in Google Maps.
- Search destinations and write destination reviews.

## Project structure

```text
src/main/java/com/travelplanner
├── api          REST controllers and request models
├── model        Database entities
├── repository   Database access
└── service      Authentication and weather services

src/main/resources
├── application.properties
└── static       HTML, CSS, and JavaScript files
```

## Run locally

Requirements: Java 21, Maven, and a running MySQL server.

Start MySQL on Linux:

```bash
sudo systemctl start mysql
```

In MySQL Workbench, open a query tab and run:

```sql
CREATE DATABASE IF NOT EXISTS travel_planner;
CREATE USER IF NOT EXISTS 'travelplanner'@'localhost' IDENTIFIED BY 'travelplanner';
ALTER USER 'travelplanner'@'localhost' IDENTIFIED BY 'travelplanner';
GRANT ALL PRIVILEGES ON travel_planner.* TO 'travelplanner'@'localhost';
```

These are SQL statements for Workbench's query tab, not commands for the Linux terminal. If you use a different MySQL username or password, set `DB_USERNAME` and `DB_PASSWORD` before starting the app.

From the project folder, run:

```bash
mvn spring-boot:run
```

Open <http://localhost:8080> and create an account. MySQL stores the app data. Weather forecasts and Google Maps routes need an internet connection.

## Main technologies

- Java 21
- Spring Boot
- Spring Data JPA
- MySQL
- HTML, CSS, and JavaScript
