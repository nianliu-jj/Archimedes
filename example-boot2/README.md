# Archimedes Example (Spring Boot 2.7 / javax)

This module is a small Spring Boot 2.7.x application used to verify `archimedes-spring-boot-2-starter` as an application dependency. It mirrors the endpoints of the SB3 `example` module.

## Run

```bash
mvn -pl example-boot2 -am spring-boot:run
```

If your environment does not have a reactor build available, install the root artifact first and then run the example module:

```bash
mvn install
mvn -f example-boot2/pom.xml spring-boot:run
```

## Useful URLs

- Example UI: http://localhost:8081/archimedes
- API contract JSON: http://localhost:8081/archimedes/apis

The port is 8081 so this app can run side by side with the SB3 `example` (port 8080).
