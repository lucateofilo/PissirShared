# PissirShared

Progetto PISSIR 2025/26: sistema di monitoraggio e gestione del traffico ferroviario.

- [Architettura](docs/architettura.md)
- [Validazione](docs/validazione.md)
- [Relazione (bozza)](docs/relazione.md)

## Requisiti

- Java 21
- Maven 3.9
- Docker (per Mosquitto e PostgreSQL)
- OpenSSL (per creare i certificati)

Gli script sono in bash: su Windows vanno lanciati da Git Bash o da WSL.

## Avvio

```bash
infra/certs/genera-certificati.sh                  # solo la prima volta
docker compose -f infra/docker-compose.yml up -d   # broker e database
./launch.sh                                        # compila e avvia sensori, stazioni, treni e centrale
```

La pagina della centrale è su https://localhost:8443. Il certificato è firmato dalla CA del progetto, quindi il browser chiede di accettarlo (oppure si importa `infra/certs/ca.crt`). Utenti di prova: `tecnico` / `tecnico` e `admin` / `admin`.

I log di ogni processo sono nella cartella `logs/`. Per fermare i processi: `./stop.sh`. Per fermare broker e database: `docker compose -f infra/docker-compose.yml down`.

## Simulare i guasti

```bash
./sim.sh guasto B                 # guasto permanente nella stazione B
./sim.sh sensore-rotto C IN       # il sensore di ingresso di C smette di funzionare
./sim.sh sensore-ok C IN
./sim.sh blocca 102               # il treno 102 si ferma tra due stazioni
./sim.sh sblocca 102
```

Gli scenari completi, con i risultati attesi, sono in [docs/validazione.md](docs/validazione.md).

## Test

```bash
mvn test
```

## Moduli

| Modulo | Contenuto |
|---|---|
| `common` | Client MQTT con TLS, nomi dei topic, messaggi |
| `sensor` | Sensore di ingresso o di uscita di una stazione |
| `station` | Nodo stazione |
| `train` | Simulatore di un treno |
| `occ` | Centrale operativa: ingestion, prossima fermata, guasti, allarmi, API REST, pagina web |
| `infra` | Docker Compose, configurazione di Mosquitto, certificati, schema e dati del database |
