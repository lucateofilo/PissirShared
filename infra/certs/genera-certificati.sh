#!/usr/bin/env bash
# Crea la CA del progetto e il certificato usato dal broker e dalla centrale (validi un anno).
# I file generati restano in questa cartella e non vanno caricati su git.
set -e
cd "$(dirname "$0")"

# Autorità di certificazione (CA) del progetto
openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
    -subj "/CN=PISSIR Rail CA" -keyout ca.key -out ca.crt

# Certificato del server, firmato dalla CA. Vale per localhost e per il nome del container.
openssl req -newkey rsa:2048 -nodes -subj "/CN=localhost" -keyout server.key -out server.csr
printf "subjectAltName=DNS:localhost,DNS:mosquitto,IP:127.0.0.1\n" > server.ext
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -days 365 -extfile server.ext -out server.crt
rm server.csr server.ext ca.srl

# Il broker nel container gira con un altro utente e deve poter leggere la chiave
chmod 644 server.key
echo "Certificati creati in infra/certs"
