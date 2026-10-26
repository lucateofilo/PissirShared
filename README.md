# PissirShared

Progetto PISSIR 2025/26: sistema di monitoraggio e gestione del traffico ferroviario.

- [Architettura](docs/architettura.md)
- [Validazione](docs/validazione.md)
- [Relazione (bozza)](docs/relazione.md)

## Avvio

Serve solo Docker Desktop: Java, Maven e i certificati sono gestiti dentro i container.

1. Installare [Docker Desktop](https://www.docker.com/products/docker-desktop/) e aprirlo.
2. Scaricare il progetto, con `git clone` oppure come [ZIP](https://github.com/lucateofilo/PissirShared/archive/refs/heads/claude/ciao-aw87py.zip) da estrarre.
3. Aprire un terminale nella cartella del progetto (su Windows va bene PowerShell) e lanciare:

```bash
docker compose up --build -d
```

La prima volta ci vogliono alcuni minuti, perché Docker scarica le immagini e compila il progetto. Le volte successive basta `docker compose up -d`.

La pagina della centrale è su https://localhost:8443. Il certificato è firmato dalla CA del progetto, quindi il browser chiede di accettarlo. Utenti di prova: `tecnico` / `tecnico` e `admin` / `admin`.

I treni partono 20 secondi dopo l'avvio e il viaggio dura circa un minuto.

## Comandi utili

```bash
docker compose ps                             # stato dei servizi
docker compose logs -f occ                    # log della centrale (oppure station-b, train-101, ...)
docker compose start train-101 train-102      # ripete il viaggio dei treni
docker compose down                           # ferma tutto
docker compose down -v                        # ferma tutto e cancella database e code del broker
```

## Simulare i guasti

```bash
docker compose exec mosquitto sim guasto B              # guasto permanente nella stazione B
docker compose exec mosquitto sim sensore-rotto C IN    # il sensore di ingresso di C smette di funzionare
docker compose exec mosquitto sim sensore-ok C IN
docker compose exec mosquitto sim blocca 102            # il treno 102 si ferma tra due stazioni
docker compose exec mosquitto sim sblocca 102
docker compose kill station-b                           # la stazione B cade
docker compose pause station-c                          # la stazione C si blocca (riprende con unpause)
docker compose stop occ                                 # la centrale si spegne (riparte con start)
```

Gli scenari completi, con i risultati attesi, sono in [docs/validazione.md](docs/validazione.md).

## Se docker compose non funziona

- `unknown shorthand flag: 'f'` oppure `'compose' is not a docker command`: manca il componente Compose. Va installato Docker Desktop (non solo il comando `docker`) e va aperto almeno una volta.
- `port is already allocated`: un altro programma usa la porta 5432, 8443 o 8883. Capita se sul computer è già installato PostgreSQL.

## Sviluppo senza Docker per i servizi Java

Per modificare il codice e riavviare i servizi velocemente si possono tenere in Docker solo broker e database e lanciare i servizi Java sul computer. Serve Java 21; Maven viene scaricato da `./mvnw`. Gli script sono in bash: su Windows vanno lanciati da Git Bash.

```bash
docker compose up -d mosquitto postgres
./launch.sh        # compila e avvia sensori, stazioni, treni e centrale; log nella cartella logs/
./stop.sh
./mvnw test        # test automatici
```

## Moduli

| Modulo | Contenuto |
|---|---|
| `common` | Client MQTT con TLS, nomi dei topic, messaggi |
| `sensor` | Sensore di ingresso o di uscita di una stazione |
| `station` | Nodo stazione |
| `train` | Simulatore di un treno |
| `occ` | Centrale operativa: ingestion, prossima fermata, guasti, allarmi, API REST, pagina web |
| `infra` | Configurazione di Mosquitto, script dei certificati, schema e dati del database |
