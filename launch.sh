#!/usr/bin/env bash
# Avvia i servizi Java direttamente sul computer, senza Docker: utile durante lo sviluppo.
# Prima bisogna avviare broker e database: docker compose up -d mosquitto postgres
set -e
cd "$(dirname "$0")"

STATIONS="A B C"
# numero del treno : stazione di partenza : direzione (A = andata, R = ritorno)
TRAINS="101:A:A 102:C:R"

./mvnw -q package
mkdir -p logs
rm -f logs/pids

# Avvia un processo in background: nome del log, utente MQTT, jar e argomenti.
# Le password MQTT sono quelle di prova registrate in infra/mosquitto/passwd.
run() {
    local name=$1 user=$2
    shift 2
    MQTT_USER=$user MQTT_PASSWORD=${user}2026 \
        java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "$@" > "logs/$name.log" 2>&1 &
    echo $! >> logs/pids
}

run occ occ occ/target/occ.jar
sleep 3

for s in $STATIONS; do
    run "sensor-$s-IN" sensor sensor/target/sensor.jar "$s" IN
    run "sensor-$s-OUT" sensor sensor/target/sensor.jar "$s" OUT
    run "station-$s" station station/target/station.jar "$s"
done
sleep 2

for t in $TRAINS; do
    IFS=: read -r id start dir <<< "$t"
    run "train-$id" train train/target/train.jar "$id" "$start" "$dir"
done

echo "Sistema avviato. Log nella cartella logs/, pagina web su https://localhost:8443"
echo "Per fermare tutto: ./stop.sh"
