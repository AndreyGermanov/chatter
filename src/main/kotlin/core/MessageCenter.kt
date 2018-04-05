package core
import interactors.Users
import io.javalin.*
import io.javalin.embeddedserver.jetty.websocket.WebSocketConfig
import io.javalin.embeddedserver.jetty.websocket.WebSocketHandler
import models.DBModel
import models.Room
import models.User
import org.bson.Document
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.api.WebSocketListener
import org.eclipse.jetty.websocket.api.WebSocketPolicy
import org.eclipse.jetty.websocket.api.annotations.WebSocket
import org.eclipse.jetty.websocket.common.WebSocketSession
import org.json.simple.JSONArray
import org.json.simple.parser.JSONParser
import org.json.simple.JSONObject
import utils.Logger
import utils.toJSONString
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.zip.CRC32
import utils.LogLevel

/**
 * WebSocket server handler class. Provides set of event handlers for all events
 * of websocket server (MessageCenter instance) and handlers of all types of messages, which WebSocket server receives
 * from chat clients
 *
 * @param parent link to MessageCenter, which instatiates objects of this class to process WebSocket connections
 * @property remoteSession Link to established connection session with remote WebSocket client
 * @property msgCenter Link to owner MessageCenter
 * @property lastResponse Last response sent to remote client
 */
@WebSocket(maxTextMessageSize = 1048576, maxBinaryMessageSize = 1048576*1000)
open class MessageObject(parent:MessageCenter) : WebSocketAdapter() {

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

    var msgCenter = parent
    var app = ChatApplication
    var lastResponse:String = ""
    var remoteSession: Session? = null

    /**
     * Handles chat user registration requests
     * @param params object with user registration fields
     * @return JSON object with resulting status of registration ("ok" or "error" and additional message about error)
     */
    fun registerUser(params:JSONObject): JSONObject {
        Logger.log(LogLevel.DEBUG,"Begin processing register_user request. " +
                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                "Request body: $params", "MessageCenter","registerUser");
        var response = JSONObject()
        var status = "error"
        var message = ""
        app.users.register(params) { result,user ->
            Logger.log(LogLevel.DEBUG,"Register user request sent to database. " +
                    "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                    "Result: $result. Registered record: $user", "MessageCenter","registerUser")
            if (result is Users.UserRegisterResultCode) {
                Logger.log(LogLevel.DEBUG,"Register user response from DB has correct format: $result " +
                        "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                        "MessageCenter","registerUser")
                response.set("status_code", result.toString())
                if (result == Users.UserRegisterResultCode.RESULT_OK) {
                    status = "ok"
                }
                message = result.getMessage()
            } else {
                Logger.log(LogLevel.WARNING,"Register user response does not have correct format. " +
                        "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                        "Response body: $result", "MessageCenter","registerUser")
                response.set("status_code",Users.UserRegisterResultCode.RESULT_ERROR_UNKNOWN)
                message = "Unknown error. Contact support"
            }
        }
        response.set("status",status)
        response.set("message",message)
        Logger.log(LogLevel.DEBUG,"Return result of register user request. " +
                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                "Response body: $response", "MessageCenter","registerUser")
        return response
    }

    /**
     * Handles chat user login requests
     * @param params object with login and password, provided by user
     * @return JSON object with resulting status of login process, user profile data and list of rooms
     */
    fun loginUser(params:JSONObject) : JSONObject {
        Logger.log(LogLevel.DEBUG,"Begin processing login_user request. " +
                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                "Request body: $params","MessageCenter","loginUser")
        var response = JSONObject()
        var status = "error"
        var status_code: Users.UserLoginResultCode = Users.UserLoginResultCode.RESULT_OK
        if (!params.contains("login") || params.get("login").toString().isEmpty()) {
            status_code = Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN
        } else if (!params.contains("password") || params.get("password").toString().isEmpty()) {
            status_code = Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD
        } else {
            Logger.log(LogLevel.DEBUG,"Sending login user request to Users interactor","MessageCenter","loginuser")
            app.users.login(params.get("login").toString(),params.get("password").toString()) { result_code, user ->
                Logger.log(LogLevel.DEBUG,"Received result after processing by Users interactor. " +
                        "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                        "Result: $result_code", "MessageCenter","loginUser")
                if (result_code != Users.UserLoginResultCode.RESULT_OK) {
                    Logger.log(LogLevel.DEBUG,"Received error from Users interactor." +
                            "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                            "MessageCenter","loginUser")
                    status_code = result_code
                } else {
                    Logger.log(LogLevel.DEBUG,"Received success result from Users interactor. " +
                            "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                            "Preparing response","MessageCenter","loginUser")
                    status = "ok"
                    val sessionObj = app.sessions.getBy("user_id",user!!["_id"].toString())
                    if (sessionObj != null) {
                        val session = sessionObj as models.Session
                        response.set("session_id",session["_id"])
                    } else {
                        Logger.log(LogLevel.WARNING,"Could not get session_id for user which logged" +
                                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                                "MessageCenter", "loginUser")
                    }
                    response.set("login",user!!["login"])
                    response.set("email",user!!["email"])
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
                    if (user!!["role"]!=null) {
                        response.set("role",user["role"])
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
                    Logger.log(LogLevel.DEBUG,"Prepared login_user response before process profile image: $response" +
                            "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                            "MessageCenter","loginUser")
                    if (Files.exists(Paths.get(app.usersPath+"/"+user["_id"]+"/profile.png"))) {
                        val stream = FileInputStream(app.usersPath+"/"+user["_id"]+"/profile.png")
                        val img = stream.readBytes()
                        val checksumEngine = CRC32()
                        checksumEngine.update(img)
                        response.set("checksum",checksumEngine.value.toString())
                        response.set("file",img)
                        Logger.log(LogLevel.DEBUG,"Prepared login_user response with profile image " +
                                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}." +
                                "Response: $response")
                    } else {
                        Logger.log(LogLevel.DEBUG,"Profile image not found for user. " +
                                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                                "User body: $user", "MessageCenter","loginUser")
                    }
                }
            }
        }
        response.set("status",status)
        response.set("status_code",status_code.toString())
        response.set("message",status_code.getMessage())
        Logger.log(LogLevel.DEBUG,"Return final response for login_user request. " +
                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                "Request body: $params, Response body: $response", "MessageCenter", "loginUser")
        return response
    }

    /**
     * Handles chat user update profile requests
     * @param params object with user registration fields
     * @return JSON object with resulting status of update ("ok" or "error" and additional message about error)
     */
    fun updateUser(params:JSONObject): JSONObject {
        val result = JSONObject()
        var status = "error"
        var status_code = Users.UserUpdateResultCode.RESULT_OK
        var message = ""
        var field = ""
        Logger.log(LogLevel.DEBUG,"Begin processing update_user request. " +
                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                "Request body: $params", "MessageCenter","updateUser")
        app.users.updateUser(params) { result_code,msg ->
            Logger.log(LogLevel.DEBUG,"Receive response from Users interactor: $result_code,'$msg' " +
                    "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                    "MessageCenter","updateUser")
            status_code = result_code
            if (status_code != Users.UserUpdateResultCode.RESULT_OK) {
                Logger.log(LogLevel.DEBUG,"Received error response from Users interactor: $result_code, '$msg' " +
                        "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                        "MessageCenter","updateUser")
                if (status_code == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY ||
                        status_code == Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE) {
                    field = msg
                    message = status_code.getMessage()+ " " + field
                } else {
                    message = status_code.getMessage()
                }
            } else {
                if (params.contains("profile_image_checksum") && params.get("profile_image_checksum").toString().isEmpty()) {
                    Logger.log(LogLevel.WARNING,"Received error response from Users interactor " +
                            "(empty profile_image_checksum)" +
                            "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                            "MessageCenter","updateUser")
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
                            Logger.log(LogLevel.DEBUG,"Received successful error response from Users interactor " +
                                    "(incorrect profile_image_checksum) " +
                                    "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                                    "MessageCenter","updateUser")
                            status = "error"
                            field = "profile_image_checksum"
                            status_code = Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE
                            message = "Error with profile image. Please, try again"
                        }
                    }
                    if (checksum>0) {
                        params.set("request_timestamp",System.currentTimeMillis()/1000)
                        val pending_request = HashMap<String,Any>()
                        if (this.remoteSession?.remote!=null) {
                            pending_request.set("session", this.remoteSession!!.remote)
                        } else {
                            pending_request.set("session","")
                        }
                        pending_request.set("request",params)
                        this.msgCenter.file_requests.set(checksum, pending_request)
                        Logger.log(LogLevel.DEBUG,"Add record to 'file_requests' queue for checksum: $checksum. " +
                                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                                "Request body: ${JSONObject(pending_request)}")
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
        Logger.log(LogLevel.DEBUG,"Return result of update_user request. " +
                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                "Request body: $params. Result body: $result",
                "MessageCenter","updateUser")
        return result
    }

    /**
     * Handles actions, related to user login, which not require to have correct user_id and session_id
     * in request body
     *
     * @param action  Action to process
     * @param request Action request params to process
     * @return result of operation as JSON Object
     */
    fun handleUserLoginRequests(action: Users.UserLoginAction,request:JSONObject):JSONObject {
        Logger.log(LogLevel.DEBUG,"Begin processing unauthorized initial user request. " +
                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                "Action: $action. Request: $request","MessageCenter","handleUserLoginRequests")
        var response = JSONObject()
        when(action) {
            Users.UserLoginAction.register_user -> {
                val result = registerUser(request)
                if (result.contains("status")) {
                    result.set("request_id", request.get("request_id"))
                    response = result
                }
            }
            Users.UserLoginAction.login_user -> {
                val result = loginUser(request)
                if (result.contains("status")) {
                    result.set("request_id", request.get("request_id"))
                    response = result
                }
            }
        }
        Logger.log(LogLevel.DEBUG,"Begin processing unauthorized initial user request. " +
                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                "Action: $action. Request: $request, Response: $response","MessageCenter","handleUserLoginRequests")
        return response
    }

    /**
     * Function used to handle requests from users after login (required to have correct user_id
     * and session_id)
     *
     * @param action  Action to process
     * @param request Action request params to process
     * @return result of operation as JSON Object
     */
    fun handleUserAppRequests(action: Users.UserAppAction, request:JSONObject):JSONObject {
        Logger.log(LogLevel.DEBUG,"Begin processing authorized user request. " +
                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                "Action: $action. Request: $request","MessageCenter","handleUserAppRequests")
        var response = JSONObject()
        val system_error_response = JSONObject()
        system_error_response.set("status","error")
        system_error_response.set("status_code",MessageObjectResponseCodes.INTERNAL_ERROR)
        system_error_response.set("message",MessageObjectResponseCodes.INTERNAL_ERROR.getMessage())
        if (!request.containsKey("user_id") || app.users.getById(request.get("user_id").toString()) == null) {
            system_error_response.set("status_code", MessageObjectResponseCodes.AUTHENTICATION_ERROR)
            system_error_response.set("message", MessageObjectResponseCodes.AUTHENTICATION_ERROR.getMessage())
            response = system_error_response
            return response
        }
        if (!request.containsKey("session_id") || app.sessions.getById(request.get("session_id").toString()) == null) {
            system_error_response.set("status_code", MessageObjectResponseCodes.AUTHENTICATION_ERROR)
            system_error_response.set("message", MessageObjectResponseCodes.AUTHENTICATION_ERROR.getMessage())
            response = system_error_response
            return response
        }
        val user_session = app.sessions.getById(request.get("session_id").toString()) as models.Session
        if (user_session["user_id"] != request.get("user_id").toString()) {
            system_error_response.set("status_code", MessageObjectResponseCodes.AUTHENTICATION_ERROR)
            system_error_response.set("message", MessageObjectResponseCodes.AUTHENTICATION_ERROR.getMessage())
            response = system_error_response
            return response
        }
        when (action) {
            Users.UserAppAction.update_user -> {
                val result = updateUser(request)
                if (result.contains("status")) {
                    result.set("action",request.get("action"))
                    result.set("request_id", request.get("request_id"))
                    response = result
                }
            }
        }
        Logger.log(LogLevel.DEBUG,"Finish processing authorized user request. " +
                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                "Action: $action. Request: $request, Response: $response","MessageCenter","handleUserAppRequests")
        return response
    }

    /**
     * Handler of websocket text events
     * @param message Received text message
     */
    override fun onWebSocketText(message: String?) {
        
        Logger.log(LogLevel.DEBUG,"Received request to WebSocket server. " +
                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName}" +
                "Response string: $message", "MessageCenter","onWebSocketText")
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
                Logger.log(LogLevel.WARNING,"Error while parse request to JSON. " +
                        "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName}" +
                        "Response string: $message", "MessageCenter","onWebSocketText")
                response = system_error_response
            }
            if (obj.containsKey("request_id") && !obj.get("request_id").toString().isEmpty()) {
                response["request_id"] = obj["request_id"].toString()
                system_error_response["request_id"] = obj["request_id"].toString()
                val action = obj.get("action").toString()
                if (Users.UserLoginAction.Is(action)!=null) {
                    response = this.handleUserLoginRequests(Users.UserLoginAction.valueOf(action),obj)
                } else if (Users.UserAppAction.Is(action)!=null) {
                    response = this.handleUserAppRequests(Users.UserAppAction.valueOf(action),obj)
                } else {
                    system_error_response.set("status_code", MessageObjectResponseCodes.INTERNAL_ERROR)
                    response = system_error_response
                }
                if (obj.containsKey("action")) {
                    response["action"] = obj["action"].toString()
                    system_error_response["action"] = obj["action"].toString()
                }
            } else {
                Logger.log(LogLevel.WARNING,"Received request with incorrect request_id. " +
                        "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName}" +
                        "Response string: $message", "MessageCenter","onWebSocketText")
                response = system_error_response
            }
        } else {
            Logger.log(LogLevel.WARNING,"Received empty request without body. " +
                    "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName}" +
                    "Response string: $message",
                    "MessageCenter","onWebSocketText")
            response = system_error_response
        }
        var file:ByteArray? = null
        if (response.contains("file") && response.get("file") is ByteArray) {
            file = response.get("file") as ByteArray
            response.remove("file")
        }
        lastResponse = toJSONString(response)
        if (this.remoteSession?.remote!=null) {
            this.remoteSession?.remote!!.sendString(lastResponse)
            Logger.log(LogLevel.DEBUG,"Sent response to client ${this.remoteSession?.remote?.inetSocketAddress?.hostName}. " +
                    "Response body: $lastResponse", "MessageCenter","onWebSocketText")
        } else {
            Logger.log(LogLevel.WARNING,"Could not send text response to request: $message. No remote session instance",
                    "MessageCenter","onWebSocketText")
        }
        if (file != null) {
            if (this.remoteSession?.remote!=null) {
                this.remoteSession?.remote!!.sendBytes(ByteBuffer.wrap(file))
                Logger.log(LogLevel.DEBUG,"Sent file to client ${this.remoteSession?.remote?.inetSocketAddress?.hostName}",
                        "MessageCenter","onWebSocketText")
            } else {
                Logger.log(LogLevel.WARNING,"Could not send file response to request: $message. No remote session instance",
                        "MessageCenter","onWebSocketText")
            }
        }
    }


    /** Handler which fires when server receives binary data
     * @param payload Binary data as ByteArray
     * @param offset Starting offset where data begins
     * @param len Length of data in bytes
     */
    override fun onWebSocketBinary(payload: ByteArray?, offset: Int, len: Int) {
        Logger.log(LogLevel.DEBUG,"Received binary request to WebSocket server. " +
                "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                "MessageCenter","onWebSocketBinary")
        if (payload != null) {
            var checkSumEngine = CRC32()
            checkSumEngine.update(payload)
            val checksum = checkSumEngine.value
            Logger.log(LogLevel.DEBUG,"Received binary request with checksum $checksum to WebSocket server. " +
                    "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                    "MessageCenter","onWebSocketBinary")
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
                Logger.log(LogLevel.DEBUG,"Prepared response to send back to client. " +
                        "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}." +
                        "Response body: $lastResponse",
                        "MessageCenter","onWebSocketBinary")
                if (session!=null) {
                    session!!.remote.sendString(lastResponse)
                    Logger.log(LogLevel.DEBUG,"Sent response to client. " +
                            "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}." +
                            "Response body: $lastResponse", "MessageCenter","onWebSocketBinary")
                } else {
                    Logger.log(LogLevel.WARNING,"Could not send response to client. Remote session not exist " +
                            "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}." +
                            "Response body: $lastResponse","MessageCenter","onWebSocketBinary")

                }
            } else {
                Logger.log(LogLevel.WARNING,"Received binary request with checksum $checksum not found . " +
                        "in 'file_requests' queue " +
                        "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                        "MessageCenter","onWebSocketBinary")
            }
        } else {
            Logger.log(LogLevel.WARNING,"Received empty binary request to WebSocket server. " +
                    "Remote IP: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}" +
                    "MessageCenter","onWebSocketBinary")
        }
    }

    /**
     * Handler which fires when websocket server establishes connection with client
     * @param session Link to session object
     */
    override fun onWebSocketConnect(session: Session?) {
        if (session != null) {
            this.remoteSession = session
            Logger.log(LogLevel.DEBUG,"Received connection handshake request from client. " +
                    "Host: ${session.remote.inetSocketAddress.hostName}", "MessageCenter","onWebSocketClientConnect")
            session.policy.maxBinaryMessageSize = 99999999
        }
    }

    /** Handler which fires on close websocket connection
     * @param statusCode status of how connection closed (org.eclipse.jetty.websocket.api.StatusCode enumeration value)
     * @param reason String description
     */
    override public fun onWebSocketClose(statusCode: Int, reason: String?) {
        Logger.log(LogLevel.DEBUG,"Received disconnection request from client. " +
                "Host: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                "MessageCenter","onWebSocketClientDisconnect")
        if (reason != null) {
        }
    }

    /**
     * Handler of websocket errors
     * @param cause Error exception
     */
    override public fun onWebSocketError(cause: Throwable) {
        Logger.log(LogLevel.WARNING,"Error in WebSocketOperation. Cause:${cause.message}." +
                "Host: ${this.remoteSession?.remote?.inetSocketAddress?.hostName ?: ""}",
                "MessageCenter","onWebSocketError")
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
            Logger.log(LogLevel.DEBUG,"Starting timer task","MessageCenter","run")
            this@MessageCenter.processFileRequestsQueue()
        }
    }

    /**
     * Timer task which used to clean outdated file upload pending requests
     */
    fun processFileRequestsQueue() {
        var toRemove = ArrayList<Long>()
        Logger.log(LogLevel.DEBUG,"Starting cleanup of file requests queue","MessageCenter","processFileRequestsQueue")
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
                Logger.log(LogLevel.DEBUG,"Timeout in file for request ${request.get("request_id")}",
                        "MessageCenter","processFileRequestsQueue")
                val response = JSONObject()
                response.set("status","error")
                response.set("status_code",Users.UserUpdateResultCode.RESULT_ERROR_IMAGE_UPLOAD)
                response.set("request_id",request.get("request_id"))
                response.set("message",Users.UserUpdateResultCode.RESULT_ERROR_IMAGE_UPLOAD.getMessage())
                Logger.log(LogLevel.DEBUG,"Prepare error response related to this file to client: $response",
                        "MessageCenter","processFileRequestsQueue")
                toRemove.add(checksum)
                if (session!=null) {
                    session.remote.sendString(toJSONString(response))
                    Logger.log(LogLevel.DEBUG,"Sent file related error response to client." +
                            "Host: ${session?.remote?.inetSocketAddress?.hostName ?: ""}."+
                            "Response: $response", "MessageCenter","processFileRequestsQueue")
                }
            }
        }
        for (item_index in toRemove) {
            val pending_request = file_requests[item_index] as HashMap<String, Any>
            var session: Session? = null
            try {
                session = pending_request.get("session") as Session
            } catch (e: Exception) {

            }
            file_requests.remove(item_index)
            Logger.log(LogLevel.DEBUG,"Remove file for file_requests queue." +
                    "Host: ${session?.remote?.inetSocketAddress?.hostName ?: ""}."+
                    "Request: $pending_request", "MessageCenter","processFileRequestsQueue")
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
        var pol = WebSocketPolicy.newServerPolicy()
        pol.maxBinaryMessageSize = 99999999
        srv.ws("/websocket",wsHandler)

        /**
         * HTTP endpoint to activate user account by link. (It's not websocket, but logically
         * correct, to put this to the same module with user register and login functions
         */
        app.webServer.get("/activate/:token", { res ->
            Logger.log(LogLevel.DEBUG,"Receive user activation request with token ${res.param("token").toString()}.",
                    "MessageCenter","/activate/:token")
            app.users.activate(res.param("token").toString()) { result_code ->
                Logger.log(LogLevel.DEBUG,"Processed user activation request with token ${res.param("token").toString()}." +
                        "Result code: ${result_code.getHttpResponseCode()}.Message: ${result_code.getMessage()}",
                        "MessageCenter","/activate/:token")
                res.status(result_code.getHttpResponseCode())
                res.html("<html><head></head><body>"+result_code.getMessage()+"</body></html>")
            }
        })
    }

}