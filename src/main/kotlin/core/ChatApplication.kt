package core
import io.javalin.Javalin
import io.javalin.embeddedserver.Location

/**
 * Created by andrey on 2/9/18.
 */

object ChatApplication {

    lateinit var webServer : Javalin

    lateinit var msgServer: MessageCenter

    lateinit var dBServer: DB

    fun run() {
        this.dBServer = DB()
        //Users.application = this
        Rooms.application = this
        webServer = Javalin.create()
        webServer.port(8080)
        webServer.enableStaticFiles("/var/www/html/public",Location.EXTERNAL)
        msgServer = MessageCenter()
        Rooms.loadRooms { ->
            webServer.start()
            webServer.get("/activate/:token", { res ->
                Users.activateUser(res.param("token").toString())
                res.status(200)
                res.html("<html><head></head><body>Account activated. Please, login to Chatter</body></html>")
                if (msgServer.user_sessions.containsKey(res.param("token").toString())) {
                    //msgServer.user_sessions.get(res.param("token").toString())!!.active = true
                }
            })

        }
    }
}