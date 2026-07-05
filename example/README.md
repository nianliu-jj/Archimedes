# Archimedes Example

This module is a small Spring Boot application used to verify Archimedes as an application dependency.

## Run

```bash
mvn -pl example -am spring-boot:run
```

If your environment does not have a reactor build available, install the root artifact first and then run the example module:

```bash
mvn install
mvn -f example/pom.xml spring-boot:run
```

## Useful URLs

- Example UI: http://localhost:8080/archimedes
- API metadata JSON: http://localhost:8080/archimedes/apis
- Demo REST endpoint: http://localhost:8080/api/users

