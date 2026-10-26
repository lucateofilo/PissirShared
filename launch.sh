#!/usr/bin/env bash
# Avvia tutti i processi della demo.
# Prima bisogna avviare broker e database: docker compose -f infra/docker-compose.yml up -d
set -e
cd "$(dirname "$0")"

STATIONS="A B C"
# numero del treno : stazione di partenza : direzione (A = andata, R = ritorno)
TRAINS="101:A:A 102:C:R"

mvn -q package
mkdir -p logs
rm -f logs/pids

run() {
    local name=$1
    shift
    java -jar "$@" > "logs/$name.log" 2>&1 &
    echo $! >> logs/pids
}

run occ occ/target/occ.jar
sleep 3

for s in $STATIONS; do
    run "sensor-$s-IN" sensor/target/sensor.jar "$s" IN
    run "sensor-$s-OUT" sensor/target/sensor.jar "$s" OUT
    run "station-$s" station/target/station.jar "$s"
done
sleep 2

for t in $TRAINS; do
    IFS=: read -r id start dir <<< "$t"
    run "train-$id" train/target/train.jar "$id" "$start" "$dir"
done

echo "Sistema avviato. Log nella cartella logs/, pagina web su http://localhost:8080"
echo "Per fermare tutto: ./stop.sh"
