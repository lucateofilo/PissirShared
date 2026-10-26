#!/bin/sh
# Simula guasti durante la demo. Gira dentro il container del broker, dove è installato come "sim":
#
#   docker compose exec mosquitto sim guasto <stazione> [tipo]          guasto permanente (predefinito: TRACK_BROKEN)
#   docker compose exec mosquitto sim sensore-rotto <stazione> <IN|OUT> il sensore smette di funzionare
#   docker compose exec mosquitto sim sensore-ok <stazione> <IN|OUT>    il sensore torna a funzionare
#   docker compose exec mosquitto sim blocca <treno>                    il treno si ferma tra due stazioni
#   docker compose exec mosquitto sim sblocca <treno>                   il treno riparte

publish() {
    mosquitto_pub -h localhost -p 8883 --cafile /mosquitto/certs/ca.crt \
        -u sim -P sim2026 -q 1 -t "$1" -m "$2"
}

case "$1" in
    guasto)        publish "rail/station/$2/sim" "{\"cmd\":\"fault\",\"kind\":\"${3:-TRACK_BROKEN}\"}" ;;
    sensore-rotto) publish "rail/station/$2/sensor/$3/sim" '{"cmd":"kill"}' ;;
    sensore-ok)    publish "rail/station/$2/sensor/$3/sim" '{"cmd":"revive"}' ;;
    blocca)        publish "rail/train/$2/sim" '{"cmd":"stop"}' ;;
    sblocca)       publish "rail/train/$2/sim" '{"cmd":"resume"}' ;;
    *)             sed -n '2,8p' "$0"; exit 1 ;;
esac
