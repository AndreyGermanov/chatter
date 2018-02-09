package core
import io.javalin.Javalin
import io.javalin.embeddedserver.Location

/**
 * Created by andrey on 2/9/18.
 */

object ChatApplication {

    lateinit var webServer : Javalin

    lateinit var msgServer: MessageCenter

    fun run() {
        this.webServer = Javalin.create()
        this.msgServer = MessageCenter()
        this.webServer.enableStaticFiles("/var/www/html/public",Location.EXTERNAL)
        this.webServer.port(8080).start()
    }
}