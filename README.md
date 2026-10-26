# PissirShared

Progetto PISSIR 2025/26: sistema di monitoraggio e gestione del traffico ferroviario.

- [Architettura](docs/architettura.md)
- [Validazione](docs/validazione.md)
- [Relazione (bozza)](docs/relazione.md)

## Requisiti

- Java 21
- Docker (per Mosquitto e PostgreSQL)
- OpenSSL (per creare i certificati)

Maven non va installato: il progetto contiene il Maven Wrapper (`./mvnw`), che lo scarica da solo al primo uso.

Gli script sono in bash: su Windows vanno lanciati da Git Bash o da WSL.

### Installazione

Windows (PowerShell come amministratore; poi riavviare il PC e aprire Docker Desktop):

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install Git.Git
winget install Docker.DockerDesktop
```

Su Windows i comandi successivi, compresi gli script del progetto, si lanciano da Git Bash, che include anche OpenSSL.

macOS (con [Homebrew](https://brew.sh); poi aprire Docker dalle Applicazioni):

```bash
brew install git
brew install --cask temurin@21 docker
```

Ubuntu / Debian:

```bash
sudo apt update
sudo apt install openjdk-21-jdk git openssl docker.io docker-compose-v2
sudo usermod -aG docker $USER   # per usare docker senza sudo; poi uscire e rientrare
```

Per controllare che sia tutto installato:

```bash
java -version            # deve indicare la versione 21
docker compose version
openssl version
```

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
./mvnw test
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
