// La pagina rilegge stazioni e treni ogni 2 secondi.

function formatTime(value) {
    return value ? new Date(value).toLocaleTimeString('it-IT') : '-';
}

function cell(text) {
    const td = document.createElement('td');
    td.textContent = text ?? '-';
    return td;
}

function fillTable(tbodyId, rows, columns) {
    const tbody = document.getElementById(tbodyId);
    tbody.replaceChildren();
    for (const row of rows) {
        const tr = document.createElement('tr');
        for (const column of columns) {
            tr.appendChild(cell(column(row)));
        }
        tbody.appendChild(tr);
    }
}

async function refresh() {
    try {
        const [stations, trains] = await Promise.all([
            fetch('/api/stations').then(r => r.json()),
            fetch('/api/trains').then(r => r.json())
        ]);
        fillTable('stations', stations, [
            s => s.id,
            s => s.name,
            s => s.state,
            s => formatTime(s.last_heartbeat)
        ]);
        fillTable('trains', trains, [
            t => t.id,
            t => t.state,
            t => t.direction === 'R' ? 'Ritorno' : 'Andata',
            t => t.last_station,
            t => t.next_station
        ]);
    } catch (e) {
        console.error('Aggiornamento fallito', e);
    }
}

refresh();
setInterval(refresh, 2000);
