-- Dati di prova: una linea con tre stazioni e due treni.
-- I tempi sono in secondi e volutamente brevi, per vedere la demo in pochi minuti.

INSERT INTO station (id, name) VALUES
    ('A', 'Novara'),
    ('B', 'Vercelli'),
    ('C', 'Torino Porta Nuova');

INSERT INTO train (id) VALUES ('101'), ('102');

-- Andata A -> B -> C, ritorno C -> B -> A
INSERT INTO route (id, train_id, direction) VALUES
    (1, '101', 'A'),
    (2, '101', 'R'),
    (3, '102', 'A'),
    (4, '102', 'R');

INSERT INTO route_stop (route_id, seq, station_id, travel_time_sec, dwell_sec) VALUES
    (1, 1, 'A', 0, 0),  (1, 2, 'B', 20, 5), (1, 3, 'C', 25, 0),
    (2, 1, 'C', 0, 0),  (2, 2, 'B', 25, 5), (2, 3, 'A', 20, 0),
    (3, 1, 'A', 0, 0),  (3, 2, 'B', 20, 5), (3, 3, 'C', 25, 0),
    (4, 1, 'C', 0, 0),  (4, 2, 'B', 25, 5), (4, 3, 'A', 20, 0);

SELECT setval('route_id_seq', 4);
