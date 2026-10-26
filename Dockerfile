# Compila tutti i moduli e prepara un'immagine con i jar dei servizi.
# La usano tutti i servizi Java del docker-compose.yml, ognuno con il proprio comando.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/sensor/target/sensor.jar /src/station/target/station.jar \
                  /src/train/target/train.jar /src/occ/target/occ.jar ./
