# Validazione

Abbiamo verificato il sistema in due modi: con test automatici sulle parti che non dipendono dalla rete, e con scenari di prova eseguiti sul sistema completo. Gli scenari sono gli stessi che mostreremo all'esame.

## Test automatici

Si lanciano con `./mvnw test`.

| Test | Cosa verifica |
|---|---|
| `ScheduleTest` | Orari previsti di arrivo e partenza, fermata successiva, capolinea, stazione non presente nella tratta |
| `MessagesTest` | I messaggi convertiti in JSON e riletti restano uguali; i campi sconosciuti vengono ignorati; un JSON non valido dà errore |
| `TopicsTest` | La centrale riceve i transiti di tutte le stazioni ma non i loro heartbeat; una stazione riceve solo gli eventi dei propri sensori |

## Preparazione degli scenari

```bash
docker compose up --build -d
```

Si apre https://localhost:8443 (il browser segnala il certificato firmato dalla nostra CA: va accettato oppure va importato `infra/certs/ca.crt`). Si entra come `tecnico` / `tecnico` oppure `admin` / `admin`.

Per seguire i messaggi sul broker:

```bash
docker compose exec mosquitto \
    mosquitto_sub -h localhost -p 8883 --cafile /mosquitto/certs/ca.crt -u occ -P occ2026 -t 'rail/#' -v
```

Con i dati di prova il treno 101 va da A a C e il treno 102 da C ad A. I tempi sono brevi: da A a B 20 secondi, sosta in B 5 secondi, da B a C 25 secondi. Il viaggio completo dura circa un minuto.

Nella tabella "Risultato" riportiamo cosa abbiamo osservato eseguendo ogni scenario.

## Scenari

### 1. Viaggio normale

Passi: avviare il sistema e aspettare un minuto.

Atteso: i treni partono dall'origine, si fermano in B e arrivano al capolinea. La pagina mostra per ogni treno stato, ultima e prossima stazione, arrivo previsto e ritardo. Alla fine i treni risultano "Arrivato al capolinea".

Risultato: confermato. Esempio di transiti registrati nel database:

| Treno | Stazione | Tipo | Orario reale | Previsto | Ritardo |
|---|---|---|---|---|---|
| 101 | A | uscita | 15:55:01 | 15:55:01 | 0 s |
| 101 | B | ingresso | 15:55:20 | 15:55:21 | -2 s |
| 101 | B | uscita | 15:55:25 | 15:55:26 | -1 s |
| 101 | C | ingresso | 15:55:50 | 15:55:51 | -1 s |
| 102 | C | uscita | 15:55:01 | 15:55:01 | 0 s |
| 102 | B | ingresso | 15:55:28 | 15:55:26 | 1 s |
| 102 | A | ingresso | 15:55:53 | 15:55:51 | 2 s |

### 2. Guasto permanente e riparazione

Passi: subito dopo la partenza dei treni `docker compose exec mosquitto sim guasto B`. Quando i treni sono fermi in B, il tecnico preme "Invia operatori".

Atteso: compaiono l'allarme e il guasto "Da riparare"; la stazione B risulta "Guasta". I treni diretti in B ricevono un avviso e, arrivati in B, restano fermi. Dopo l'invio degli operatori la stazione passa "In riparazione" e dopo 20 secondi torna attiva; i treni ripartono.

Risultato: confermato. Log del treno 101:

```
Avviso dalla centrale: Guasto nella stazione B, il treno verrà fermato all'arrivo
Arrivato in stazione B
Fermo in stazione B: Guasto in stazione B: TRACK_BROKEN
Partito da B verso C, arrivo tra 26.542 s
Avviso dalla centrale: Ritardo di 26 s
```

Abbiamo verificato anche che l'amministratore non può inviare gli operatori (risposta 403) e che non si possono inviare due volte per lo stesso guasto (risposta 400).

### 3. Sensore guasto

Passi: `docker compose exec mosquitto sim sensore-rotto C IN`, dopo un minuto `docker compose exec mosquitto sim sensore-ok C IN`.

Atteso: entro 6 secondi la stazione C passa a "Sensori guasti" con il dettaglio "Sensori non raggiungibili: IN" e compare un allarme. Il treno 101 arriva in C ma il sensore non lo rileva, quindi la centrale non riceve l'ingresso e dopo 15 secondi oltre l'arrivo previsto segnala il treno come fermo tra B e C. Riattivato il sensore, la stazione torna attiva.

Risultato: confermato. Allarmi generati:

```
Stazione C: Sensori non raggiungibili: IN
Treno 101 fermo tra le stazioni B e C
```

### 4. Treno fermo tra due stazioni

Passi: mentre il treno 102 viaggia da B ad A, `docker compose exec mosquitto sim blocca 102`; dopo l'allarme, `docker compose exec mosquitto sim sblocca 102`.

Atteso: 15 secondi dopo l'arrivo previsto compare l'allarme "Treno 102 fermo tra le stazioni B e A". Sbloccato, il treno arriva ad A e riceve l'avviso del ritardo.

Risultato: confermato. Il treno è arrivato con 39 secondi di ritardo e ha ricevuto l'avviso "Ritardo di 39 s".

### 5. Stazione che cade

Passi: terminare una stazione con `docker compose kill station-b` e riavviarla con `docker compose start station-b`.

Atteso: il broker pubblica il Last Will della stazione e la centrale genera l'allarme "Stazione B non raggiungibile (Connessione persa)". Riavviata la stazione, torna attiva. Se il processo resta bloccato senza chiudere la connessione (`docker compose pause station-c`, oppure `kill -STOP` sul processo), la centrale se ne accorge comunque, al più tardi dopo 15 secondi senza heartbeat.

Risultato: confermato in entrambi i casi. Con `kill -STOP` la centrale ha generato "Stazione C non raggiungibile (nessun heartbeat da 15 s)". Ripreso il processo con `kill -CONT`, la stazione è passata per qualche secondo a "Sensori guasti", perché mentre era bloccata non riceveva gli heartbeat dei sensori, ed è poi tornata attiva.

### 6. Centrale spenta durante il viaggio

Passi: mentre i treni viaggiano, `docker compose stop occ`; dopo 20 secondi `docker compose start occ`.

Atteso: i messaggi inviati mentre la centrale era spenta restano nel broker, grazie a QoS 1 e alla sessione persistente, e arrivano alla riaccensione. Nessun transito va perso.

Risultato: confermato. Alla riaccensione la centrale ha ricevuto in pochi secondi i quattro transiti avvenuti mentre era spenta. Le richieste di prossima fermata rimaste in coda hanno ricevuto risposta, quindi i treni in attesa sono ripartiti, con un ritardo pari al tempo in cui la centrale era spenta.

### 7. Soppressione di un treno

Passi: con un guasto in B e il treno 101 fermo in B, l'amministratore preme "Sopprimi".

Atteso: il treno riceve l'ordine e termina; nella pagina risulta "Soppresso". Un treno in viaggio non si può sopprimere.

Risultato: confermato. Il tentativo su un treno in viaggio è stato rifiutato con "Si possono sopprimere solo i treni fermi in stazione".

### 8. Gestione delle tratte

Passi: come amministratore, creare una tratta per un nuovo treno, modificarla ed eliminarla.

Atteso: la tratta compare nella tabella. Vengono rifiutate una tratta con una stazione inesistente e una seconda tratta per lo stesso treno nella stessa direzione. Il tecnico non può modificare le tratte.

Risultato: confermato. Risposte ottenute: creazione 201, stazione inesistente 400, tratta doppia 400, modifica 204, eliminazione 204, seconda eliminazione 404, creazione da parte del tecnico 403.

### 9. Sicurezza

Passi: provare ad accedere senza login, con password sbagliata, senza TLS e con un utente MQTT che scrive su un topic non suo.

Atteso e risultato:

| Prova | Esito |
|---|---|
| API senza login | 401 "Accesso richiesto" |
| Login con password sbagliata | 401 "Nome utente o password errati" |
| Pagina web in HTTP | connessione rifiutata: la centrale risponde solo in HTTPS sulla porta 8443 |
| Broker senza TLS (porta 1883) | connessione rifiutata |
| Broker con password sbagliata | "not authorised" |
| L'utente `train` pubblica su `rail/station/Z/transit` | il broker scarta il messaggio: chi è iscritto al topic non lo riceve |
