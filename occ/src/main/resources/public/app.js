// Pagina della centrale operativa. Dopo il login le tabelle vengono aggiornate ogni 2 secondi.

let user = null;            // utente collegato: { username, role }
let selectedStation = null; // stazione di cui mostrare i treni attesi
let editingRouteId = null;  // tratta in modifica, null se si sta creando una tratta nuova

const LABELS = {
    ONLINE: 'Attiva', DEGRADED: 'Sensori guasti', FAULT: 'Guasta', REPAIRING: 'In riparazione',
    OFFLINE: 'Non raggiungibile', IN_STATION: 'In stazione', RUNNING: 'In viaggio',
    ARRIVED: 'Arrivato al capolinea', SUPPRESSED: 'Soppresso', OPEN: 'Da riparare',
    DISPATCHED: 'Operatori inviati', A: 'Andata', R: 'Ritorno'
};

// Chiamate al server

async function api(method, url, body) {
    const options = { method, headers: {} };
    if (body !== undefined) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }
    const response = await fetch(url, options);
    if (response.status === 401 && url !== '/api/login') {
        showLogin();
    }
    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.title || response.statusText);
    }
    return response.status === 200 ? response.json() : null;
}

// Esegue un'azione dell'utente e mostra l'eventuale errore
async function action(method, url, body) {
    document.getElementById('action-error').textContent = '';
    try {
        await api(method, url, body);
        await refresh();
    } catch (e) {
        document.getElementById('action-error').textContent = e.message;
    }
}

// Formattazione

function label(value) {
    return LABELS[value] ?? value ?? '-';
}

function formatTime(value) {
    return value ? new Date(value).toLocaleTimeString('it-IT') : '-';
}

// Ritardo in secondi: positivo se il treno è in ritardo, negativo se è in anticipo
function formatDelay(seconds) {
    if (seconds > 0) return '+' + seconds + ' s';
    if (seconds < 0) return seconds + ' s';
    return 'in orario';
}

function formatStops(stops) {
    return stops.map(s => s.stationId).join(' - ');
}

function button(text, onClick) {
    const b = document.createElement('button');
    b.textContent = text;
    b.addEventListener('click', onClick);
    return b;
}

// Riempie una tabella: ogni colonna è una funzione che riceve la riga
// e restituisce un testo, un bottone oppure null.
function fillTable(tbodyId, rows, columns) {
    const tbody = document.getElementById(tbodyId);
    tbody.replaceChildren();
    for (const row of rows) {
        const tr = document.createElement('tr');
        for (const column of columns) {
            const td = document.createElement('td');
            const value = column(row);
            if (value instanceof Node) {
                td.appendChild(value);
            } else {
                td.textContent = value ?? '';
            }
            tr.appendChild(td);
        }
        tbody.appendChild(tr);
    }
}

// Aggiornamento delle tabelle

async function refresh() {
    if (!user) return;
    try {
        const [alerts, faults, stations, trains, routes] = await Promise.all([
            api('GET', '/api/alerts'),
            api('GET', '/api/faults'),
            api('GET', '/api/stations'),
            api('GET', '/api/trains'),
            api('GET', '/api/routes')
        ]);
        showAlerts(alerts);
        showFaults(faults);
        showStations(stations);
        showTrains(trains);
        showRoutes(routes);
        if (selectedStation) {
            showExpected(await api('GET', '/api/stations/' + selectedStation + '/expected'));
        }
    } catch (e) {
        console.error('Aggiornamento fallito', e);
    }
}

function showAlerts(alerts) {
    fillTable('alerts', alerts, [
        a => formatTime(a.ts),
        a => a.message
    ]);
}

function showFaults(faults) {
    fillTable('faults', faults, [
        f => f.station_id,
        f => f.kind,
        f => label(f.status),
        f => formatTime(f.opened_at),
        f => f.dispatched_by,
        f => user.role === 'TECH' && f.status === 'OPEN'
            ? button('Invia operatori', () => action('POST', '/api/faults/' + f.id + '/dispatch'))
            : null
    ]);
}

function showStations(stations) {
    fillTable('stations', stations, [
        s => s.id,
        s => s.name,
        s => label(s.state),
        s => s.detail,
        s => formatTime(s.last_heartbeat),
        s => button('Treni attesi', () => {
            selectedStation = s.id;
            refresh();
        })
    ]);
}

function showExpected(expected) {
    document.getElementById('expected').hidden = false;
    document.getElementById('expected-station').textContent = selectedStation;
    fillTable('arriving', expected.arriving, [
        t => t.id,
        t => t.last_station,
        t => formatTime(t.expected_arrival),
        t => formatDelay(t.delay_sec)
    ]);
    fillTable('in-station', expected.inStation, [
        t => t.id,
        t => t.next_station ?? 'capolinea',
        t => formatDelay(t.delay_sec)
    ]);
}

function showTrains(trains) {
    fillTable('trains', trains, [
        t => t.id,
        t => label(t.state),
        t => label(t.direction),
        t => t.last_station,
        t => t.next_station,
        t => formatTime(t.expected_arrival),
        t => formatDelay(t.delay_sec),
        t => user.role === 'ADMIN' && t.state === 'IN_STATION'
            ? button('Sopprimi', () => {
                if (confirm('Sopprimere il treno ' + t.id + '?')) {
                    action('POST', '/api/trains/' + t.id + '/suppress');
                }
            })
            : null
    ]);
}

function showRoutes(routes) {
    fillTable('routes', routes, [
        r => r.trainId,
        r => label(r.direction),
        r => formatStops(r.stops),
        r => user.role === 'ADMIN' ? adminRouteButtons(r) : null
    ]);
}

function adminRouteButtons(route) {
    const span = document.createElement('span');
    span.append(
        button('Modifica', () => editRoute(route)),
        ' ',
        button('Elimina', () => {
            if (confirm('Eliminare la tratta del treno ' + route.trainId + '?')) {
                action('DELETE', '/api/routes/' + route.id);
            }
        }));
    return span;
}

// Modulo delle tratte (solo amministratore)

const routeForm = document.getElementById('route-form');

function editRoute(route) {
    editingRouteId = route.id;
    document.getElementById('route-form-title').textContent = 'Modifica tratta del treno ' + route.trainId;
    routeForm.trainId.value = route.trainId;
    routeForm.direction.value = route.direction;
    routeForm.trainId.disabled = true;
    routeForm.direction.disabled = true;
    routeForm.stops.value = route.stops
        .map(s => s.stationId + ' ' + s.travelTimeSec + ' ' + s.dwellSec)
        .join('\n');
    routeForm.scrollIntoView();
}

function resetRouteForm() {
    editingRouteId = null;
    document.getElementById('route-form-title').textContent = 'Nuova tratta';
    routeForm.reset();
    routeForm.trainId.disabled = false;
    routeForm.direction.disabled = false;
}

// Trasforma le righe "B 20 5" in fermate { stationId, travelTimeSec, dwellSec }
function parseStops(text) {
    return text.split('\n')
        .map(line => line.trim())
        .filter(line => line !== '')
        .map(line => {
            const [stationId, travel, dwell] = line.split(/\s+/);
            return { stationId, travelTimeSec: Number(travel ?? 0), dwellSec: Number(dwell ?? 0) };
        });
}

routeForm.addEventListener('submit', async event => {
    event.preventDefault();
    const stops = parseStops(routeForm.stops.value);
    if (editingRouteId === null) {
        await action('POST', '/api/routes', {
            trainId: routeForm.trainId.value,
            direction: routeForm.direction.value,
            stops
        });
    } else {
        await action('PUT', '/api/routes/' + editingRouteId, { stops });
    }
    if (document.getElementById('action-error').textContent === '') {
        resetRouteForm();
    }
});

document.getElementById('route-cancel').addEventListener('click', resetRouteForm);

// Accesso

function showLogin() {
    user = null;
    document.getElementById('app').hidden = true;
    document.getElementById('login-form').hidden = false;
}

function showApp(loggedUser) {
    user = loggedUser;
    document.getElementById('user').textContent =
        user.username + ' (' + (user.role === 'ADMIN' ? 'amministratore' : 'tecnico') + ')';
    for (const element of document.querySelectorAll('.admin-only')) {
        element.hidden = user.role !== 'ADMIN';
    }
    document.getElementById('login-form').hidden = true;
    document.getElementById('app').hidden = false;
    refresh();
}

document.getElementById('login-form').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.target;
    document.getElementById('login-error').textContent = '';
    try {
        showApp(await api('POST', '/api/login', {
            username: form.username.value,
            password: form.password.value
        }));
        form.reset();
    } catch (e) {
        document.getElementById('login-error').textContent = e.message;
    }
});

document.getElementById('logout').addEventListener('click', async () => {
    await api('POST', '/api/logout');
    selectedStation = null;
    document.getElementById('expected').hidden = true;
    showLogin();
});

// All'apertura della pagina controlla se la sessione è ancora valida
api('GET', '/api/me').then(showApp).catch(showLogin);
setInterval(refresh, 2000);
