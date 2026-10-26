#!/usr/bin/env bash
# Simula guasti durante la demo, inviando comandi MQTT tramite il container del broker.
#
#   ./sim.sh guasto <stazione> [tipo]          guasto permanente (tipo predefinito: TRACK_BROKEN)
#   ./sim.sh sensore-rotto <stazione> <IN|OUT> il sensore smette di funzionare
#   ./sim.sh sensore-ok <stazione> <IN|OUT>    il sensore torna a funzionare
#   ./sim.sh blocca <treno>                    il treno si ferma tra due stazioni
#   ./sim.sh sblocca <treno>                   il treno riparte
cd "$(dirname "$0")"

publish() {
    docker compose -f infra/docker-compose.yml exec -T mosquitto \
        mosquitto_pub -h localhost -p 8883 --cafile /mosquitto/config/certs/ca.crt \
        -u sim -P sim2026 -q 1 -t "$1" -m "$2"
}

case "$1" in
    guasto)        publish "rail/station/$2/sim" "{\"cmd\":\"fault\",\"kind\":\"${3:-TRACK_BROKEN}\"}" ;;
    sensore-rotto) publish "rail/station/$2/sensor/$3/sim" '{"cmd":"kill"}' ;;
    sensore-ok)    publish "rail/station/$2/sensor/$3/sim" '{"cmd":"revive"}' ;;
    blocca)        publish "rail/train/$2/sim" '{"cmd":"stop"}' ;;
    sblocca)       publish "rail/train/$2/sim" '{"cmd":"resume"}' ;;
    *)             sed -n '2,9p' "$0"; exit 1 ;;
esac
