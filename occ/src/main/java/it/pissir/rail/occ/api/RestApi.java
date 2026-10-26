package it.pissir.rail.occ.api;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.staticfiles.Location;
import io.javalin.security.RouteRole;
import jakarta.servlet.http.HttpSession;
import it.pissir.rail.occ.db.AlertDao;
import it.pissir.rail.occ.db.Database;
import it.pissir.rail.occ.db.FaultDao;
import it.pissir.rail.occ.db.RouteDao;
import it.pissir.rail.occ.db.StationDao;
import it.pissir.rail.occ.db.TrainDao;
import it.pissir.rail.occ.db.UserDao;
import it.pissir.rail.occ.fault.FaultManager;
import it.pissir.rail.occ.traffic.Schedule;
import it.pissir.rail.occ.traffic.TrafficLogic;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * API REST per l'interfaccia web. La SPA è servita dalla cartella resources/public.
 * L'utente fa login e il server ricorda nella sessione chi è e che ruolo ha.
 */
public class RestApi {

    private static final Logger log = LoggerFactory.getLogger(RestApi.class);

    enum Role implements RouteRole { TECH, ADMIN }

    record LoginRequest(String username, String password) {
    }

    record RouteRequest(String trainId, String direction, List<Schedule.Stop> stops) {
    }

    private final StationDao stations;
    private final TrainDao trains;
    private final RouteDao routes;
    private final FaultDao faults;
    private final AlertDao alerts;
    private final UserDao users;
    private final TrafficLogic traffic;
    private final FaultManager faultManager;

    public RestApi(Database db, TrafficLogic traffic, FaultManager faultManager) {
        this.stations = new StationDao(db);
        this.trains = new TrainDao(db);
        this.routes = new RouteDao(db);
        this.faults = new FaultDao(db);
        this.alerts = new AlertDao(db);
        this.users = new UserDao(db);
        this.traffic = traffic;
        this.faultManager = faultManager;
    }

    public void start(int port) {
        Javalin app = Javalin.create(config -> config.staticFiles.add("/public", Location.CLASSPATH));
        app.beforeMatched(this::checkAccess);
        app.exception(Database.DatabaseException.class, (e, ctx) -> {
            log.error("Errore del database", e);
            ctx.status(500).json(Map.of("title", "Errore del database"));
        });

        app.post("/api/login", this::login);
        app.post("/api/logout", this::logout);
        app.get("/api/me", this::me, Role.TECH, Role.ADMIN);

        app.get("/api/stations", ctx -> ctx.json(stations.findAll()), Role.TECH, Role.ADMIN);
        app.get("/api/stations/{id}/expected", this::expectedTrains, Role.TECH, Role.ADMIN);
        app.get("/api/trains", ctx -> ctx.json(trains.findAll()), Role.TECH, Role.ADMIN);
        app.post("/api/trains/{id}/suppress", this::suppressTrain, Role.ADMIN);

        app.get("/api/routes", ctx -> ctx.json(routes.findAll()), Role.TECH, Role.ADMIN);
        app.post("/api/routes", this::createRoute, Role.ADMIN);
        app.put("/api/routes/{id}", this::updateRoute, Role.ADMIN);
        app.delete("/api/routes/{id}", this::deleteRoute, Role.ADMIN);

        app.get("/api/faults", ctx -> ctx.json(faults.findActive()), Role.TECH, Role.ADMIN);
        app.post("/api/faults/{id}/dispatch", this::dispatchOperators, Role.TECH);

        app.get("/api/alerts", ctx -> ctx.json(alerts.findLatest(20)), Role.TECH, Role.ADMIN);

        app.start(port);
    }

    /** Controlla che l'utente abbia fatto login e che il suo ruolo possa usare l'endpoint. */
    private void checkAccess(Context ctx) {
        Set<RouteRole> allowed = ctx.routeRoles();
        if (allowed.isEmpty()) {
            return;
        }
        String role = ctx.sessionAttribute("role");
        if (role == null) {
            throw new UnauthorizedResponse("Accesso richiesto");
        }
        if (!allowed.contains(Role.valueOf(role))) {
            throw new ForbiddenResponse("Operazione non permessa al tuo ruolo");
        }
    }

    // Accesso

    private void login(Context ctx) {
        LoginRequest request = ctx.bodyAsClass(LoginRequest.class);
        UserDao.User user = request.username() == null ? null : users.find(request.username());
        if (user == null || request.password() == null || !BCrypt.checkpw(request.password(), user.passwordHash())) {
            throw new UnauthorizedResponse("Nome utente o password errati");
        }
        // Dopo il login si usa sempre una sessione nuova, così un id di sessione rubato prima non vale più
        endSession(ctx);
        ctx.sessionAttribute("username", user.username());
        ctx.sessionAttribute("role", user.role());
        ctx.json(Map.of("username", user.username(), "role", user.role()));
    }

    private void logout(Context ctx) {
        endSession(ctx);
        ctx.status(204);
    }

    private static void endSession(Context ctx) {
        HttpSession session = ctx.req().getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private void me(Context ctx) {
        ctx.json(Map.of("username", ctx.sessionAttribute("username"), "role", ctx.sessionAttribute("role")));
    }

    // Stazioni e treni

    private void expectedTrains(Context ctx) {
        String stationId = ctx.pathParam("id");
        ctx.json(Map.of(
                "arriving", trains.findArrivingAt(stationId),
                "inStation", trains.findInStation(stationId)));
    }

    private void suppressTrain(Context ctx) {
        if (!traffic.suppress(ctx.pathParam("id"))) {
            throw new BadRequestResponse("Si possono sopprimere solo i treni fermi in stazione");
        }
        ctx.status(204);
    }

    private void dispatchOperators(Context ctx) {
        if (!faultManager.dispatch(intParam(ctx), ctx.sessionAttribute("username"))) {
            throw new BadRequestResponse("Guasto inesistente o operatori già inviati");
        }
        ctx.status(204);
    }

    // Tratte

    private void createRoute(Context ctx) {
        RouteRequest request = ctx.bodyAsClass(RouteRequest.class);
        if (request.trainId() == null || request.trainId().isBlank()) {
            throw new BadRequestResponse("Manca il numero del treno");
        }
        if (!"A".equals(request.direction()) && !"R".equals(request.direction())) {
            throw new BadRequestResponse("La direzione deve essere A (andata) o R (ritorno)");
        }
        if (routes.findSchedule(request.trainId(), request.direction()) != null) {
            throw new BadRequestResponse("Il treno ha già una tratta in questa direzione");
        }
        routes.create(request.trainId().trim(), request.direction(), checkStops(request.stops()));
        ctx.status(201);
    }

    private void updateRoute(Context ctx) {
        RouteRequest request = ctx.bodyAsClass(RouteRequest.class);
        if (!routes.updateStops(intParam(ctx), checkStops(request.stops()))) {
            throw new NotFoundResponse("Tratta inesistente");
        }
        ctx.status(204);
    }

    private void deleteRoute(Context ctx) {
        if (!routes.delete(intParam(ctx))) {
            throw new NotFoundResponse("Tratta inesistente");
        }
        ctx.status(204);
    }

    /** Controlla le fermate di una tratta. Il tempo di viaggio della prima fermata è sempre 0. */
    private List<Schedule.Stop> checkStops(List<Schedule.Stop> stops) {
        if (stops == null || stops.size() < 2) {
            throw new BadRequestResponse("Una tratta deve avere almeno due fermate");
        }
        Set<String> known = stations.findAll().stream()
                .map(row -> (String) row.get("id"))
                .collect(Collectors.toSet());
        Set<String> seen = new HashSet<>();
        for (Schedule.Stop stop : stops) {
            if (!known.contains(stop.stationId())) {
                throw new BadRequestResponse("Stazione inesistente: " + stop.stationId());
            }
            if (!seen.add(stop.stationId())) {
                throw new BadRequestResponse("La stazione " + stop.stationId() + " compare due volte");
            }
            if (stop.travelTimeSec() < 0 || stop.dwellSec() < 0) {
                throw new BadRequestResponse("I tempi non possono essere negativi");
            }
        }
        Schedule.Stop first = stops.get(0);
        List<Schedule.Stop> checked = new ArrayList<>(stops);
        checked.set(0, new Schedule.Stop(first.stationId(), 0, first.dwellSec()));
        return checked;
    }

    private static int intParam(Context ctx) {
        try {
            return Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw new BadRequestResponse("Identificativo non valido");
        }
    }
}
