package core
import com.mongodb.util.JSON
import io.javalin.*
import io.javalin.embeddedserver.*
import io.javalin.embeddedserver.jetty.websocket.WebSocketConfig
import io.javalin.embeddedserver.jetty.websocket.WsSession
import io.javalin.embeddedserver.jetty.websocket.WebSocketHandler
import kotlinx.coroutines.experimental.launch
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketListener
import org.json.simple.parser.JSONParser
import org.json.simple.JSONObject

class MessageObject : WebSocketListener {

    lateinit var session: Session
    var requests: HashMap<String,Any> = HashMap<String,Any>()

    override public fun onWebSocketError(cause: Throwable) {
    }

    override public fun onWebSocketText(message: String?) {
        if (message != null) {
            val parser = JSONParser()
            val obj: JSONObject = parser.parse(message) as JSONObject
            if (obj.containsKey("request_id")) {
               when (obj.get("action")) {
                   "register_user" -> {
                        Users.registerUser(obj) { result ->
                            val result_obj: JSONObject = JSONObject()
                            val it = result.iterator()
                            while (it.hasNext()) {
                                var item = it.next();
                                result_obj.set(item.key,item.value)
                            }
                            result_obj.set("request_id",obj.get("request_id"))
                            session.remote.sendString(result_obj.toString())
                        }
                   }
                   "login_user" -> {
                       Users.loginUser(obj) { result ->
                           val result_obj: JSONObject = JSONObject()
                           val it = result.iterator()
                           while (it.hasNext()) {
                               var item = it.next();
                               result_obj.set(item.key,item.value)
                           }
                           result_obj.set("request_id",obj.get("request_id"))
                           session.remote.sendString(result_obj.toString())
                       }
                   }
               }
            } else {
                session.remote.sendString("HELLO!")
            }
        }
    }

    override public fun onWebSocketConnect(session: Session?) {
        if (session != null) {
            this.session = session
        }
    }

    override public fun onWebSocketClose(statusCode: Int, reason: String?) {
        if (reason != null) {
        }
    }

    override public fun onWebSocketBinary(payload: ByteArray?, offset: Int, len: Int) {
        if (payload != null) {
        }
    }
}

class MessageCenter {

    init {
        val srv: Javalin = ChatApplication.webServer
        srv.ws("/websocket", MessageObject())

    }
}