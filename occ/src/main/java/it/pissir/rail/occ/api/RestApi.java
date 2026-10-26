package it.pissir.rail.occ.api;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import it.pissir.rail.occ.db.Database;
import it.pissir.rail.occ.db.StationDao;
import it.pissir.rail.occ.db.TrainDao;

/** API REST per l'interfaccia web. La SPA è servita dalla cartella resources/public. */
public class RestApi {

    private final StationDao stations;
    private final TrainDao trains;

    public RestApi(Database db) {
        this.stations = new StationDao(db);
        this.trains = new TrainDao(db);
    }

    public void start(int port) {
        Javalin app = Javalin.create(config -> config.staticFiles.add("/public", Location.CLASSPATH));

        app.get("/api/stations", ctx -> ctx.json(stations.findAll()));
        app.get("/api/trains", ctx -> ctx.json(trains.findAll()));

        app.start(port);
    }
}
