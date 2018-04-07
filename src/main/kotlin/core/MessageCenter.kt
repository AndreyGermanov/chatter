package core
import controllers.ActionRouter
import interactors.Users
import io.javalin.*
import io.javalin.embeddedserver.jetty.websocket.WebSocketConfig
import io.javalin.embeddedserver.jetty.websocket.WebSocketHandler
import io.javalin.embeddedserver.jetty.websocket.WsSession
import models.DBModel
import models.Room
import models.User
import org.bson.Document
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.api.WebSocketListener
import org.eclipse.jetty.websocket.api.WebSocketPolicy
import org.eclipse.jetty.websocket.api.annotations.*
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
 * WebSocket and HTTP server, used for all communications with browser and mobile chat clients
 *
 * @property file_requests Hashmap of requests, which is waiting for file. Key is checksum of file, value is body of
 * request, which is waiting for this file. Request is pending until file with checksum received
 * @property app Link to main application object
 * @property wsHandler Object delegate which used to server WebSocket client connection
 * @property cronjobTimer Cronjob timer object, used to run requests queue cleanup
 * @property CRONJOB_TIME_PERIOD Delay betwen cronjob runs in seconds
 * @property PENDING_REQUEST_TIMEOUT Timeout in seconds, after which file upload requests becomes outdated and a subject
 * @property remoteSession Link to established connection session with remote WebSocket client
 * @property msgCenter Link to owner MessageCenter
 * @property lastResponse Last response sent to remote client
 * to be removed by cleanup cronjob
 */
object MessageCenter {

    var file_requests = HashMap<Long,Any>()
    val app = ChatApplication
    lateinit var cronjobTimer:Timer
    var CRONJOB_TIME_PERIOD = 5
    var PENDING_REQUEST_TIMEOUT = 10
    var lastResponse:String = ""
    var remoteSession: Session? = null

    /** Timer task class, which used to run cronjob for cleanup
     *  pending file requests queue
     */
    class fileQueueProcessor: TimerTask() {
        override fun run() {
            Logger.log(LogLevel.DEBUG,"Starting timer task","MessageCenter","run")
            MessageCenter.processFileRequestsQueue()
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

    fun setup() {
        val srv: Javalin = ChatApplication.webServer
        var pol = WebSocketPolicy.newServerPolicy()
        pol.maxBinaryMessageSize = 9999999
        Logger.log(LogLevel.DEBUG,"Setup Message Center","MessageCenter","init")
        srv.ws("/websocket", MessageObject())

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

    /**
     * Handler of websocket text events
     * @param message: Received text message
     * @param session: Link to client session
     */
    fun onWebSocketText(message: String?,session:Session? = null) {
        Logger.log(LogLevel.DEBUG,"Received text request to WebSocket server. " +
                "Remote IP: ${session?.remote?.inetSocketAddress?.hostName}" +
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
                        "Remote IP: ${session?.remote?.inetSocketAddress?.hostName}" +
                        "Request string: $message", "MessageCenter","onWebSocketText")
                response = system_error_response
            }
            if (!obj.containsKey("action") || obj["action"].toString().isEmpty()) {
                Logger.log(LogLevel.WARNING,"Received request without action. " +
                        "Remote IP: ${session?.remote?.inetSocketAddress?.hostName}" +
                        "Request : $obj", "MessageCenter","onWebSocketText")
                response = system_error_response
            } else {
                response = ActionRouter.processAction(obj,session)
                Logger.log(LogLevel.DEBUG,"Received response from ActionRouter " +
                        "Request : $obj, action : ${obj["action"]}, response: $response",
                        "MessageCenter","onWebSocketText")
                system_error_response["action"] = obj["action"].toString()
            }
            if (obj.containsKey("request_id")) {
                system_error_response["request_id"] = obj["request_id"].toString()
            }
        } else {
            Logger.log(LogLevel.WARNING,"Received empty request without body. " +
                    "Remote IP: ${session?.remote?.inetSocketAddress?.hostName}" +
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
        if (session?.remote!=null) {
            session?.remote!!.sendString(lastResponse)
            Logger.log(LogLevel.DEBUG,"Sent response to client ${session?.remote?.inetSocketAddress?.hostName}. " +
                    "Response body: $lastResponse", "MessageCenter","onWebSocketText")
        } else {
            Logger.log(LogLevel.WARNING,"Could not send text response to request: $message. No remote session instance",
                    "MessageCenter","onWebSocketText")
        }
        if (file != null) {
            if (session?.remote!=null) {
                session?.remote!!.sendBytes(ByteBuffer.wrap(file))
                Logger.log(LogLevel.DEBUG,"Sent file to client ${session?.remote?.inetSocketAddress?.hostName}",
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
     * @param session Link to client session
     */
    fun onWebSocketBinary(payload: ByteArray?, offset: Int, len: Int,session:Session?=null) {
        Logger.log(LogLevel.DEBUG,"Received binary request to WebSocket server. " +
                "Remote IP: ${session?.remote?.inetSocketAddress?.hostName ?: ""}",
                "MessageCenter","onWebSocketBinary")
        if (payload != null) {
            var checkSumEngine = CRC32()
            checkSumEngine.update(payload)
            val checksum = checkSumEngine.value
            Logger.log(LogLevel.DEBUG,"Received binary request with checksum $checksum to WebSocket server. " +
                    "Remote IP: ${session?.remote?.inetSocketAddress?.hostName ?: ""}",
                    "MessageCenter","onWebSocketBinary")
            if (this.file_requests.containsKey(checksum)) {
                var pending_request = this.file_requests.get(checksum) as HashMap<String,Any>
                var request = pending_request.get("request") as JSONObject
                var queue_session:Session? = null
                try {
                    queue_session = pending_request.get("session") as Session
                } catch (e:Exception) { }
                Files.createDirectories(Paths.get("opt/chatter/users/"+request.get("user_id").toString()))
                var fs: FileOutputStream = FileOutputStream("opt/chatter/users/"+
                        request.get("user_id")+"/profile.png",false)
                fs.write(payload)
                fs.close()
                val response = JSONObject()
                response.set("status","ok")
                response.set("status_code",Users.UserUpdateResultCode.RESULT_OK)
                response.set("message",Users.UserUpdateResultCode.RESULT_OK.getMessage())
                response.set("request_id",request.get("request_id").toString())
                lastResponse = toJSONString(response)
                this.file_requests.remove(checksum)
                Logger.log(LogLevel.DEBUG,"Prepared response to send back to client. " +
                        "Remote IP: ${session?.remote?.inetSocketAddress?.hostName ?: ""}." +
                        "Response body: $lastResponse",
                        "MessageCenter","onWebSocketBinary")
                if (queue_session!=null) {
                    queue_session.remote.sendString(lastResponse)
                    Logger.log(LogLevel.DEBUG,"Sent response to client. " +
                            "Remote IP: ${queue_session?.remote?.inetSocketAddress?.hostName ?: ""}." +
                            "Response body: $lastResponse", "MessageCenter","onWebSocketBinary")
                } else {
                    Logger.log(LogLevel.WARNING,"Could not send response to client. Remote session not exist " +
                            "Remote IP: ${session?.remote?.inetSocketAddress?.hostName ?: ""}." +
                            "Response body: $lastResponse","MessageCenter","onWebSocketBinary")
                }
            } else {
                Logger.log(LogLevel.WARNING,"Received binary request with checksum $checksum not found . " +
                        "in 'file_requests' queue " +
                        "Remote IP: ${session?.remote?.inetSocketAddress?.hostName ?: ""}",
                        "MessageCenter","onWebSocketBinary")
            }
        } else {
            Logger.log(LogLevel.WARNING,"Received empty binary request to WebSocket server. " +
                    "Remote IP: ${session?.remote?.inetSocketAddress?.hostName ?: ""}" +
                    "MessageCenter","onWebSocketBinary")
        }
    }

    /**
     * Handler which fires when websocket server establishes connection with client
     * @param session Link to session object
     */
    fun onWebSocketConnect(session: Session?) {
        if (session != null) {
            Logger.log(LogLevel.DEBUG,"Received connection handshake request from client. " +
                    "Host: ${session.remote.inetSocketAddress.hostName}", "MessageCenter","onWebSocketClientConnect")
            session.policy.maxBinaryMessageSize = 99999999
        }
    }

    /** Handler which fires on close websocket connection
     * @param statusCode status of how connection closed (org.eclipse.jetty.websocket.api.StatusCode enumeration value)
     * @param reason String description
     * @param session Link to client session
     */
    fun onWebSocketClose(statusCode: Int, reason: String?,session:Session?=null) {
        Logger.log(LogLevel.DEBUG,"Received disconnection request from client. " +
                "Host: ${session?.remote?.inetSocketAddress?.hostName ?: ""}",
                "MessageCenter","onWebSocketClientDisconnect")
        if (reason != null) {
        }
    }

    /**
     * Handler of websocket errors
     * @param cause Error exception
     * @param session Link to client session
     */
    fun onWebSocketError(cause: Throwable,session:Session?=null) {
        Logger.log(LogLevel.WARNING,"Error in WebSocketOperation. Cause:${cause.message}." +
                "Host: ${session?.remote?.inetSocketAddress?.hostName}",
                "MessageCenter","onWebSocketError")
    }

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
}

/**
 * WebSocket server handler class. Provides set of event handlers for all events
 * of websocket server (MessageCenter instance) and handlers of all types of messages, which WebSocket server receives
 * from chat clients
 *
 */
@WebSocket(maxTextMessageSize = 1048576, maxBinaryMessageSize = 1048576*1000000)
open class MessageObject {
    /**
     * Called when connection to client established
     *
     * @param session: Link to client session
     */
    @OnWebSocketConnect
    open fun onConnect(session: Session) {
        Logger.log(LogLevel.DEBUG,"Receive client connection request","MessageObject","onConnect")
        MessageCenter.onWebSocketConnect(session)
    }

    /**
     * Called when client connection closed
     *
     * @param session: Link to client session
     * @param statusCode: Status code of close reason
     * @param reason: String message, describing close reason
     */
    @OnWebSocketClose
    open fun onClose(session:Session,statusCode:Int,reason:String ) {
        MessageCenter.onWebSocketClose(statusCode,reason,session)
    }

    /**
     * Called in case of error in WebSocket operations between client and server
     *
     * @param session: Link to client session
     * @param cause: Link to Exception, which describes error
     */
    @OnWebSocketError
    open fun onError(session:Session,cause:Throwable) {
        MessageCenter.onWebSocketError(cause,session)
    }

    /**
     * Called when received text message from client
     *
     * @param session: Link to client session
     * @param message: Message text
     */
    @OnWebSocketMessage
    open fun onMessage(session:Session,message:String) {
        MessageCenter.onWebSocketText(message,session)
    }

    /**
     * Called when binary data received from client
     *
     * @param session: Link to client session
     * @param buf: Array of binary data bytes
     * @param offset: Number of first byte from which actual data begins (0 by default)
     * #param length: Length of actual data in bytes (by default it's full length of "buf")
     */
    @OnWebSocketMessage
    open fun onBinary(session:Session,buf:ByteArray,offset:Int,length:Int) {
        MessageCenter.onWebSocketBinary(buf,offset,length,session)
    }
}

