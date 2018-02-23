package core
import interactors.Users
import io.javalin.*
import models.DBModel
import models.Room
import org.bson.Document
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketListener
import org.json.simple.JSONArray
import org.json.simple.parser.JSONParser
import org.json.simple.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.Adler32

/**
 * WebSocket server handler class. Provides set of event handlers for all events
 * of websocket server (MessageCenter instance) and handlers of all types of messages, which WebSocket server receives
 * from chat clients
 *
 * @param parent link to MessageCenter, which instatiates objects of this class to process WebSocket connections
 * @property session Link to established connection session with remote WebSocket client
 * @property msgCenter Link to owner MessageCenter
 */
class MessageObject(parent:MessageCenter) : WebSocketListener {

    var session: Session? = null

    var msgCenter = parent
    var app = ChatApplication
    /**
     * Handles chat user registration requests
     * @param params object with user registration fields
     * @return JSON object with resulting status of registration ("ok" or "error" and additional message about error)
     */
    public fun registerUser(params:JSONObject): JSONObject {
        var response = JSONObject()
        var status = "error"
        var message = ""
        app.users.register(params) { result,user ->
            if (result is Users.UserRegisterResultCode) {
                response.set("status_code", result.toString())
                if (result == Users.UserRegisterResultCode.RESULT_OK) {
                    status = "ok"
                }
                message = result.getMessage()
            } else {
                response.set("status_code",Users.UserRegisterResultCode.RESULT_ERROR_UNKNOWN)
                message = "Unknown error. Contact support"
            }
        }
        response.set("status",status)
        response.set("message",message)
        return response
    }

    /**
     * Handles chat user login requests
     * @param params object with login and password, provided by user
     * @return JSON object with resulting status of login process, user profile data and list of rooms
     */
    public fun loginUser(params:JSONObject) : JSONObject {
        var response = JSONObject()
        var status = "error"
        var status_code: Users.UserLoginResultCode = Users.UserLoginResultCode.RESULT_OK
        if (!params.contains("login") || params.get("login").toString().isEmpty()) {
            status_code = Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN
        } else if (!params.contains("password") || params.get("password").toString().isEmpty()) {
            status_code = Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD
        } else {
            app.users.login(params.get("login").toString(),params.get("password").toString()) { result_code, user ->
                if (result_code != Users.UserLoginResultCode.RESULT_OK) {
                    status_code = result_code
                } else {
                    status = "ok"
                    val sessionObj = app.sessions.getBy("user_id",user!!["_id"].toString())
                    if (sessionObj != null) {
                        val session = sessionObj as models.Session
                        response.set("session_id",session["_id"])
                    }
                    response.set("login",user!!["login"])
                    response.set("user_id",user!!["_id"])
                    if (user!!["first_name"]!=null) {
                        response.set("first_name",user["first_name"].toString())
                    }
                    if (user!!["last_name"]!=null) {
                        response.set("last_name",user["last_name"].toString())
                    }
                    if (user!!["gender"]!=null) {
                        response.set("gender",user["gender"].toString())
                    }
                    if (user!!["birthDate"]!=null) {
                        response.set("birthDate",user["birthDate"])
                    }
                    if (user!!["default_room"]!=null) {
                        val default_room = app.rooms.getById(user["default_room"].toString())
                        if (default_room != null) {
                            response.set("default_room",user["default_room"])
                        }
                    }
                    val rooms = JSONArray()
                    for (roomObj in app.rooms) {
                        val room = roomObj as Room
                        val roomDoc = JSONObject()
                        roomDoc.set("_id",room["_id"].toString())
                        roomDoc.set("name",room.get("name").toString())
                        rooms.add(roomDoc)
                    }
                    response.set("rooms",rooms)
                    if (Files.exists(Paths.get(app.usersPath+"/"+user["_id"]+"/profile.png"))) {
                        val stream = FileInputStream(app.usersPath+"/"+user["_id"]+"/profile.png")
                        val img = stream.readBytes()
                        val checksumEngine = Adler32()
                        checksumEngine.update(img)
                        response.set("checksum",checksumEngine.value.toString())
                        if (session!=null) {
                            session!!.remote.sendBytes(ByteBuffer.wrap(img))
                        }
                    }
                }
            }
        }
        response.set("status",status)
        response.set("status_code",status_code.toString())
        response.set("message",status_code.getMessage())
        return response
    }

    /**
     * Handles chat user update profile requests
     * @param params object with user registration fields
     * @return JSON object with resulting status of update ("ok" or "error" and additional message about error)
     */
    public fun updateUser(params:JSONObject): JSONObject {
        return JSONObject()
    }

    /**
     * Handler of websocket errors
     * @param cause Error exception
     */
    override public fun onWebSocketError(cause: Throwable) {
    }

    /**
     * Handler of websocket text events
     * @param message Received text message
     */
    override public fun onWebSocketText(message: String?) {
        if (message != null) {
            val parser = JSONParser()
            val obj: JSONObject = parser.parse(message) as JSONObject
            if (obj.containsKey("request_id")) {
               when (obj.get("action")) {
                   "register_user" -> {
                       val result = registerUser(obj)
                       if (result.contains("status")) {
                           result.set("request_id",obj.get("request_id"))
                           if (session!=null) {
                               session!!.remote.sendString(result.toString())
                           }
                       }
                   }
                   "login_user" -> {
                       val result = loginUser(obj)
                       if (result.contains("status")) {
                           result.set("request_id",obj.get("request_id"))
                           if (session!=null) {
                               session!!.remote.sendString(result.toString())
                           }

                       }
                   }
                   "update_user" -> {
                       val result = updateUser(obj)
                       if (result.contains("status")) {
                           result.set("request_id",obj.get("request_id"))
                           if (session!=null) {
                               session!!.remote.sendString(result.toString())
                           }
                       }
                   }
               }
            }
        }
    }

    /**
     * Handler which fires when websocket server establishes connection with client
     * @param session Link to session object
     */
    override public fun onWebSocketConnect(session: Session?) {
        if (session != null) {
            this.session = session
        }
    }

    /** Handler which fires on close websocket connection
     * @param statusCode status of how connection closed (org.eclipse.jetty.websocket.api.StatusCode enumeration value)
     * @param reason String description
     */
    override public fun onWebSocketClose(statusCode: Int, reason: String?) {
        if (reason != null) {
        }
    }


    /** Handler which fires when server receives binary data
     * @param payload Binary data as ByteArray
     * @param offset Starting offset where data begins
     * @param len Length of data in bytes
     */
    override public fun onWebSocketBinary(payload: ByteArray?, offset: Int, len: Int) {
        if (payload != null) {
            var checkSumEngine = Adler32()
            checkSumEngine.update(payload)
            val checksum = checkSumEngine.value
            if (msgCenter.file_requests.containsKey(checksum)) {
                var request = msgCenter.file_requests.get(checksum) as JSONObject
                Files.createDirectories(Paths.get("opt/chatter/users/"+request.get("user_id").toString()))
                var fs: FileOutputStream = FileOutputStream("opt/chatter/users/"+request.get("user_id")+"/profile.png",false)

                fs.write(payload)
                fs.close()
                if (session!=null) {
                    session!!.remote.sendString(request.toString())
                }
                msgCenter.file_requests.remove(checksum)
            }
        }
    }
}

/**
 * WebSocket and HTTP server, used for all communications with browser and mobile chat clients
 *
 * @property file_requests Hashmap of requests, which is waiting for file. Key is checksum of file, value is body of
 * request, which is waiting for this file. Request is pending until file with checksum received
 */
class MessageCenter {

    var file_requests = HashMap<Long,Any>()
    lateinit var wsHandler: WebSocketListener
    init {
        val srv: Javalin = ChatApplication.webServer
        wsHandler = MessageObject(this)
        srv.ws("/websocket", wsHandler)
    }
}