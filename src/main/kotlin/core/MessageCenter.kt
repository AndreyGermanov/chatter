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
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.Adler32

class MessageObject : WebSocketListener {

    lateinit var session: Session
    var file_requests = HashMap<Long,Any>()
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
                           Users.getUserProfileImage(result_obj.get("user_id").toString()) { image ->
                               if (image != null) {
                                   val checksumSystem = Adler32()
                                   checksumSystem.update(image)
                                   val checksum = checksumSystem.value
                                   result_obj.set("checksum",checksum)
                                   session.remote.sendString(result_obj.toString())
                                   session.remote.sendBytes(ByteBuffer.wrap(image))
                               } else {
                                   session.remote.sendString(result_obj.toString())
                               }
                           }
                       }
                   }
                   "update_user" -> {
                       Users.updateUser(obj) { result ->
                           val result_obj: JSONObject = JSONObject()
                           val it = result.iterator()
                           while (it.hasNext()) {
                               var item = it.next();
                               result_obj.set(item.key,item.value)
                           }
                           result_obj.set("request_id",obj.get("request_id"))
                           if (obj.containsKey("checksum")) {
                               file_requests.set(obj.get("checksum").toString().toLong(),result_obj)
                           } else {
                               session.remote.sendString(result_obj.toString())
                           }
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
            var checkSumEngine = Adler32()
            checkSumEngine.update(payload)
            val checksum = checkSumEngine.value
            if (file_requests.containsKey(checksum)) {
                var request = file_requests.get(checksum) as JSONObject
                Files.createDirectories(Paths.get("opt/chatter/users/"+request.get("user_id").toString()))
                var fs: FileOutputStream = FileOutputStream("opt/chatter/users/"+request.get("user_id")+"/profile.png",false)
                fs.write(payload)
                fs.close()
                session.remote.sendString(request.toString())
                file_requests.remove(checksum)
            }
        }
    }
}

class MessageCenter {

    init {
        val srv: Javalin = ChatApplication.webServer
        srv.ws("/websocket", MessageObject())

    }
}