# ==============================================================================
# Dockerfile de los microservicios de colegio-jwt (Spring Cloud).
#
# El proyecto es un build Maven multi-modulo: los 7 servicios cuelgan del mismo
# pom raiz. Por eso este UNICO Dockerfile compila los 7 modulos UNA sola vez en
# la etapa "build" y despues cada servicio copia de esa etapa nada mas que su
# propio jar. Con 7 Dockerfiles independientes, Maven resolveria el pom raiz
# siete veces (7 builds lentos e identicos).
#
#   docker compose build  -> construye las 8 imagenes: 7 servicios + frontend
#   docker compose up -d  -> levanta todo el stack
# ==============================================================================

# ---------------------------- etapa: compilacion -----------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Primero los pom.xml y despues el codigo: Maven baja las dependencias en una
# capa propia que Docker cachea. Cambiar solo codigo no re-descarga nada.
COPY pom.xml ./pom.xml
COPY backend/registry/pom.xml backend/registry/pom.xml
COPY backend/config-server/pom.xml backend/config-server/pom.xml
COPY backend/auth-service/pom.xml backend/auth-service/pom.xml
COPY backend/alumnos/pom.xml backend/alumnos/pom.xml
COPY backend/administracion/pom.xml backend/administracion/pom.xml
COPY backend/gateway/pom.xml backend/gateway/pom.xml
COPY backend/admin-server/pom.xml backend/admin-server/pom.xml
# El montaje cache guarda el repo local de Maven entre builds (BuildKit), asi no
# se re-descarga todo cada vez que cambia una linea de codigo.
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests dependency:go-offline -q || true

# Codigo fuente de los 7 modulos.
COPY backend backend

# Empaqueta los 7 jars. Los tests de arranque (contextLoads) levantan Eureka, el
# Config Server y RabbitMQ: se corren a mano con .\mvnw.cmd test, no aca.
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests -q

# ------------------------- imagenes de ejecucion -----------------------------
FROM eclipse-temurin:17-jre-alpine AS registry
WORKDIR /app
COPY --from=build /workspace/backend/registry/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS config-server
WORKDIR /app
COPY --from=build /workspace/backend/config-server/target/*.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS auth-service
WORKDIR /app
COPY --from=build /workspace/backend/auth-service/target/*.jar app.jar
EXPOSE 8100 9110
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS alumnos
WORKDIR /app
COPY --from=build /workspace/backend/alumnos/target/*.jar app.jar
EXPOSE 8101 9111
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS administracion
WORKDIR /app
COPY --from=build /workspace/backend/administracion/target/*.jar app.jar
EXPOSE 8102 9112
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS gateway
WORKDIR /app
COPY --from=build /workspace/backend/gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS admin-server
WORKDIR /app
COPY --from=build /workspace/backend/admin-server/target/*.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]