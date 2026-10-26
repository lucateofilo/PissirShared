# PissirShared

Progetto PISSIR 2025/26: sistema di monitoraggio e gestione del traffico ferroviario.

Il progetto è descritto in [docs/architettura.md](docs/architettura.md).

## Requisiti

- Java 21
- Maven 3.9
- Docker (per Mosquitto e PostgreSQL)

## Avvio

```bash
docker compose -f infra/docker-compose.yml up -d   # broker e database
./launch.sh                                        # compila e avvia sensori, stazioni, treni e centrale
```

La pagina della centrale è su http://localhost:8080. I log di ogni processo sono nella cartella `logs/`.
Per seguire i messaggi MQTT:

```bash
mosquitto_sub -t 'rail/#' -v
```

Per fermare i processi: `./stop.sh`. Per fermare broker e database: `docker compose -f infra/docker-compose.yml down`.

## Moduli

| Modulo | Contenuto |
|---|---|
| `common` | Client MQTT, nomi dei topic, messaggi |
| `sensor` | Sensore di ingresso o di uscita di una stazione |
| `station` | Nodo stazione |
| `train` | Simulatore di un treno |
| `occ` | Centrale operativa: ingestion, prossima fermata, stato delle stazioni, API REST, pagina web |
| `infra` | Docker Compose, configurazione di Mosquitto, schema e dati del database |

## Stato

Funziona il percorso base: i treni viaggiano sulla linea A, B, C seguendo le tratte del database,
la centrale registra i transiti e lo stato delle stazioni, la pagina web mostra stazioni e treni.

Da fare, nell'ordine del piano di lavoro (sezione 10 del documento):
calcolo dei ritardi, buffer locale e watchdog degli heartbeat, guasti e allarmi,
login e API complete, TLS.
