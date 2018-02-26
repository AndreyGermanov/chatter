package core
import interactors.Users
import io.javalin.*
import models.DBModel
import models.Room
import models.User
import org.bson.Document
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketListener
import org.eclipse.jetty.websocket.common.WebSocketSession
import org.json.simple.JSONArray
import org.json.simple.parser.JSONParser
import org.json.simple.JSONObject
import utils.toJSONString
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.zip.Adler32

/**
 * WebSocket server handler class. Provides set of event handlers for all events
 * of websocket server (MessageCenter instance) and handlers of all types of messages, which WebSocket server receives
 * from chat clients
 *
 * @param parent link to MessageCenter, which instatiates objects of this class to process WebSocket connections
 * @property session Link to established connection session with remote WebSocket client
 * @property msgCenter Link to owner MessageCenter
 * @property lastResponse Last response sent to remote client
 */
open class MessageObject(parent:MessageCenter) : WebSocketListener {

    /**
     * List of possible error codes during message exchanges on MessageServer level
     */
    enum class MessageObjectResponseCodes {
        INTERNAL_ERROR,
        AUTHENTICATION_ERROR;
        fun getMessage():String {
            var result = ""
            when(this) {
                INTERNAL_ERROR -> result = "Internal Error"
                AUTHENTICATION_ERROR -> result = "Authentication Error"
            }
            return result
        }
    }

    var session: Session? = null

    var msgCenter = parent
    var app = ChatApplication
    var lastResponse:String = ""



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
        val result = JSONObject()
        var status = "error"
        var status_code = Users.UserUpdateResultCode.RESULT_OK
        var message = ""
        var field = ""
        app.users.updateUser(params) { result_code,msg ->
            status_code = result_code
            if (status_code != Users.UserUpdateResultCode.RESULT_OK) {
                if (status_code == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY ||
                        status_code == Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE) {
                    field = msg
                    message = status_code.getMessage()+ " " + field
                } else {
                    message = status_code.getMessage()
                }
            } else {
                if (params.contains("profile_image_checksum") && params.get("profile_image_checksum").toString().isEmpty()) {
                    status_code = Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY
                    field = "profile_image_checksum"
                    message = "Error with profile image. Please, try again"
                } else {
                    status = "ok"
                    var checksum: Long = 0
                    if (params.contains("profile_image_checksum")) {
                        try {
                            checksum = params.get("profile_image_checksum").toString().toLong()
                        } catch (e: Exception) {
                            status = "error"
                            field = "profile_image_checksum"
                            status_code = Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE
                            message = "Error with profile image. Please, try again"
                        }
                    }
                    if (checksum>0) {
                        params.set("request_timestamp",System.currentTimeMillis()/1000)
                        val pending_request = HashMap<String,Any>()
                        if (session!=null) {
                            pending_request.set("session", session as WebSocketSession)
                        } else {
                            pending_request.set("session","")
                        }
                        pending_request.set("request",params)
                        this.msgCenter.file_requests.set(checksum, pending_request)
                        status_code = Users.UserUpdateResultCode.RESULT_OK_PENDING_IMAGE_UPLOAD
                    } else {
                        message = status_code.getMessage()
                    }
                }
            }
        }

        result.set("status",status)
        result.set("status_code",status_code)
        if (!message.isEmpty()) {
            result.set("message", message)
        }
        if (!field.isEmpty()) {
            result.set("field", field)
        }

        return result
    }

    /**
     * Handler of websocket text events
     * @param message Received text message
     */
    override public fun onWebSocketText(message: String?) {
        val system_error_response = JSONObject()
        system_error_response.set("status","error")
        system_error_response.set("status_code",MessageObjectResponseCodes.INTERNAL_ERROR)
        system_error_response.set("message",MessageObjectResponseCodes.INTERNAL_ERROR.getMessage())

        var response = JSONObject()

        if (message != null) {
            val parser = JSONParser()
            var obj = JSONObject()
            try {
                obj = parser.parse(message) as JSONObject
            } catch (e:Exception) {
                response = system_error_response
            }
            if (obj.containsKey("request_id") && !obj.get("request_id").toString().isEmpty()) {
                when (obj.get("action")) {
                    "register_user" -> {
                        val result = registerUser(obj)
                        if (result.contains("status")) {
                            result.set("request_id", obj.get("request_id"))
                            response = result
                        }
                    }
                    "login_user" -> {
                        val result = loginUser(obj)
                        if (result.contains("status")) {
                            result.set("request_id", obj.get("request_id"))
                            response = result
                        }
                    }
                    else -> {
                        if (obj.containsKey("user_id") && app.users.getById(obj.get("user_id").toString()) != null) {
                            if (obj.containsKey("session_id") && app.sessions.getById(obj.get("session_id").toString()) != null) {
                                val user_session = app.sessions.getById(obj.get("session_id").toString()) as models.Session
                                if (user_session["user_id"] == obj.get("user_id").toString()) {
                                    when (obj.get("action")) {
                                        "update_user" -> {
                                            val result = updateUser(obj)
                                            if (result.contains("status")) {
                                                result.set("request_id", obj.get("request_id"))
                                                response = result
                                            }
                                        }
                                        else -> {
                                            system_error_response.set("status_code", MessageObjectResponseCodes.INTERNAL_ERROR)
                                            response = system_error_response
                                        }
                                    }
                                } else {
                                    system_error_response.set("status_code", MessageObjectResponseCodes.AUTHENTICATION_ERROR)
                                    system_error_response.set("message", MessageObjectResponseCodes.AUTHENTICATION_ERROR.getMessage())
                                    response = system_error_response
                                }
                            } else {
                                system_error_response.set("status_code", MessageObjectResponseCodes.AUTHENTICATION_ERROR)
                                system_error_response.set("message", MessageObjectResponseCodes.AUTHENTICATION_ERROR.getMessage())
                                response = system_error_response
                            }
                        } else {
                            system_error_response.set("status_code", MessageObjectResponseCodes.AUTHENTICATION_ERROR)
                            system_error_response.set("message", MessageObjectResponseCodes.AUTHENTICATION_ERROR.getMessage())
                            response = system_error_response
                        }
                    }
                }
            } else {
                response = system_error_response
            }
        } else {
            response = system_error_response
        }
        lastResponse = toJSONString(response)
        if (session!=null) {
            session!!.remote.sendString(lastResponse)
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
                var pending_request = msgCenter.file_requests.get(checksum) as HashMap<String,Any>
                var request = pending_request.get("request") as JSONObject
                var session:Session? = null
                try {
                    session = pending_request.get("session") as Session
                } catch (e:Exception) {

                }
                Files.createDirectories(Paths.get("opt/chatter/users/"+request.get("user_id").toString()))
                var fs: FileOutputStream = FileOutputStream("opt/chatter/users/"+request.get("user_id")+"/profile.png",false)
                fs.write(payload)
                fs.close()
                val response = JSONObject()
                response.set("status","ok")
                response.set("status_code",Users.UserUpdateResultCode.RESULT_OK)
                response.set("message",Users.UserUpdateResultCode.RESULT_OK.getMessage())
                response.set("request_id",request.get("request_id").toString())
                lastResponse = toJSONString(response)
                msgCenter.file_requests.remove(checksum)
                if (session!=null) {
                    session!!.remote.sendString(lastResponse)
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

    /**
     * Handler of websocket errors
     * @param cause Error exception
     */
    override public fun onWebSocketError(cause: Throwable) {
    }

}

/**
 * WebSocket and HTTP server, used for all communications with browser and mobile chat clients
 *
 * @property file_requests Hashmap of requests, which is waiting for file. Key is checksum of file, value is body of
 * request, which is waiting for this file. Request is pending until file with checksum received
 * @property app Link to main application object
 * @property wsHandler Object delegate which used to server WebSocket client connection
 * @property cronjobTimer Cronjob timer object, used to run requests queue cleanup
 * @property CRONJOB_TIME_PERIOD Delay betwen cronjob runs in seconds
 * @property PENDING_REQUEST_TIMEOUT Timeout in seconds, after which file upload requests becomes outdated and a subject
 * to be removed by cleanup cronjob
 */
class MessageCenter {

    var file_requests = HashMap<Long,Any>()
    val app = ChatApplication
    var wsHandler: WebSocketListener
    lateinit var cronjobTimer:Timer
    var CRONJOB_TIME_PERIOD = 5
    var PENDING_REQUEST_TIMEOUT = 10

    /** Timer task class, which used to run cronjob for cleanup
     *  pending file requests queue
     */
    inner class fileQueueProcessor: TimerTask() {
        override fun run() {
            this@MessageCenter.processFileRequestsQueue()
        }
    }

    /**
     * Timer task which used to clean outdated file upload pending requests
     */
    fun processFileRequestsQueue() {
        var toRemove = ArrayList<Long>()
        for ((checksum,file_request) in file_requests) {
            val pending_request = file_request as HashMap<String, Any>
            val request = pending_request.get("request") as JSONObject
            var session: Session? = null
            try {
                session = pending_request.get("session") as Session
            } catch (e: Exception) {

            }
            val request_time = request.get("request_timestamp").toString().toInt()
            if (System.currentTimeMillis()/1000 - request_time > PENDING_REQUEST_TIMEOUT) {
                val response = JSONObject()
                response.set("status","error")
                response.set("status_code",Users.UserUpdateResultCode.RESULT_ERROR_IMAGE_UPLOAD)
                response.set("request_id",request.get("request_id"))
                response.set("message",Users.UserUpdateResultCode.RESULT_ERROR_IMAGE_UPLOAD.getMessage())
                toRemove.add(checksum)
                if (session!=null) {
                    session.remote.sendString(toJSONString(response))
                }
            }
        }
        for (item_index in toRemove) {
            file_requests.remove(item_index)
        }
    }

    /**
     * Used to run cronjobs after server initialized
     */
    fun runCronjob() {
        cronjobTimer = Timer()
        cronjobTimer.schedule(fileQueueProcessor(),0,CRONJOB_TIME_PERIOD.toLong()*1000)
    }

    init {
        val srv: Javalin = ChatApplication.webServer
        wsHandler = MessageObject(this)
        srv.ws("/websocket", wsHandler)

        /**
         * HTTP endpoint to activate user account by link. (It's not websocket, but logically
         * correct, to put this to the same module with user register and login functions
         */
        app.webServer.get("/activate/:token", { res ->
            app.users.activate(res.param("token").toString()) { result_code ->
                res.status(result_code.getHttpResponseCode())
                res.html("<html><head></head><body>"+result_code.getMessage()+"</body></html>")
            }
        })
    }

}