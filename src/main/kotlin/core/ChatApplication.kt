package core
import interactors.Rooms
import interactors.Sessions
import interactors.Users
import io.javalin.Javalin
import io.javalin.embeddedserver.Location

/**
 * Created by andrey on 2/9/18.
 */

object ChatApplication {

    lateinit var webServer : Javalin

    lateinit var msgServer: MessageCenter

    lateinit var dBServer: DB

    lateinit var users: Users

    lateinit var sessions: Sessions

    lateinit var rooms: Rooms

    var rootPath = "opt/chatter"

    var usersPath = rootPath + "/users"

    fun run() {
        this.dBServer = DB()

        this.users = Users(this.dBServer.db)

        //RoomsOld.application = this
        webServer = Javalin.create()
        webServer.port(8080)
        webServer.enableStaticFiles("/var/www/html/public",Location.EXTERNAL)
        msgServer = MessageCenter()
        /*
        RoomsOld.loadRooms { ->
            webServer.start()
            webServer.get("/activate/:token", { res ->
                UsersOld.activateUser(res.param("token").toString())
                res.status(200)
                res.html("<html><head></head><body>Account activated. Please, login to Chatter</body></html>")
                if (msgServer.user_sessions.containsKey(res.param("token").toString())) {
                    //msgServer.user_sessions.get(res.param("token").toString())!!.active = true
                }
            })

        }
        */
    }
}