package core
import io.javalin.embeddedserver.jetty.websocket.WebSocketConfig
import io.javalin.embeddedserver.jetty.websocket.WsSession

class MessageCenter() {

    init {
        ChatApplication.webServer.ws("/websocket") { ws ->

            ws.onConnect {
                println("Connected")
            }

            ws.onMessage { session, msg ->
                println(session.id)
                println(msg)
                session.send("Test response")
            }


        }
    }
}