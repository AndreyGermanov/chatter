/**
 * Created by Andrey Germanov on 4/6/18.
 */
package controllers

import core.ChatApplication
import core.MessageCenter
import interactors.Users
import models.Room
import org.eclipse.jetty.websocket.api.Session
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import utils.LogLevel
import utils.Logger
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.CRC32

/**
 * Controller which represents set of actions, related to user login and registration
 */
enum class LoginController(val value:String): WebSocketController {
    /**
     * Register user action. Handles user register requests
     */
    register_user("register_user") {
        /**
         * Action executor.
         *
         * @param request - JSONObject with user register params:
         *                  login - user login name
         *                  email - user email
         *                  password - user password
         *                  confirm_password - user confirm password
         *                  request_id - unique request ID
         * @param session - link to client WebSocket session
         * @return - JSONObject with result of registration with following fields:
         *                  status - "ok" or "error"
         *                  status_code - one of values of Users.UserRegisterResultCode enumeration
         *                  message - error message or nothing
         *                  field - if result is error and it related to some of fields, contains
         *                          string name of field with error or nothing
         */
        override fun exec(request:JSONObject,session: Session?):JSONObject {
            Logger.log(LogLevel.DEBUG,"Begin processing register_user request. " +
                    "Remote IP: $sessionIP.Request body: $request", "LoginController","register_user.exec")
            var response = JSONObject()
            var status = "error"
            var message = ""
            MessageCenter.app.users.register(request) { result, user ->
                Logger.log(LogLevel.DEBUG,"Register user request sent to database. " +
                        "Remote IP: $sessionIP" + "Result: $result. Registered record: $user",
                        "LoginController","register_user.exec")
                if (result is Users.UserRegisterResultCode) {
                    Logger.log(LogLevel.DEBUG,"Register user response from DB has correct format: $result" +
                            "Remote IP: $sessionIP.","LoginController","register_user.exec")
                    response.set("status_code", result.toString())
                    if (result == Users.UserRegisterResultCode.RESULT_OK) {
                        status = "ok"
                    }
                    message = result.getMessage()
                } else {
                    Logger.log(LogLevel.WARNING,"Register user response does not have correct format. " +
                            "Remote IP: $sessionIP},Response body: $result", "LoginController","register_user.exec")
                    response.set("status_code", Users.UserRegisterResultCode.RESULT_ERROR_UNKNOWN)
                    message = "Unknown error. Contact support"
                }
            }
            response.set("status",status)
            response.set("message",message)
            Logger.log(LogLevel.DEBUG,"Return result of register user request. " +
                    "Remote IP: $sessionIP,Response body: $response", "LoginController","register_user.exec")
            return response
        }
    },
    /**
     * Register user action. Handles user register requests
     */
    login_user("login_user") {
        /**
         * Action executor.
         *
         * @param request - JSONObject with user login params:
         *                  login - user login name
         *                  password - user password
         *                  request_id - unique request ID
         * @param session - link to client WebSocket session
         * @return - JSONObject with result of registration with following fields:
         *                  status - "ok" or "error"
         *                  status_code - one of values of Users.UserLoginResultCode enumeration
         *                  message - error message or nothing
         *                  user_id - Part of authentication token, used for all next requests of this user
         *                  session_id - Part of authentication token, used for all next requests of this user
         *                  login - Login of this user
         *                  email - Email of this user
         *                  first_name - First name of user
         *                  last_name - Last name of user
         *                  gender - Gender of user: "M" or "F"
         *                  birthDate - Birth date of user as timestamp
         *                  default_room - Chat room, which user enters by default
         *                  rooms - JSON array of chat rooms, to which user has access
         *                  role - User role. Numeric code of Users.UserRoles enumeration
         *                  checksum - Checksum of profile image which sent next after this response,
         *                              as a separate response
         *                  file - body of profile image as ByteArray
         */
        override fun exec(request:JSONObject,session: Session?):JSONObject {
            Logger.log(LogLevel.DEBUG,"Begin processing login_user request. " +
                    "Remote IP: $sessionIP,Request body: $request","LoginController","login_user.exec")
            var response = JSONObject()
            var status = "error"
            var status_code: Users.UserLoginResultCode = Users.UserLoginResultCode.RESULT_OK
            if (!request.contains("login") || request.get("login").toString().isEmpty()) {
                status_code = Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN
            } else if (!request.contains("password") || request.get("password").toString().isEmpty()) {
                status_code = Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD
            } else {
                Logger.log(LogLevel.DEBUG,"Sending login user request to Users interactor." +
                        "Remote IP: $sessionIP.","LoginController","login_user.exec")
                MessageCenter.app.users.login(request.get("login").toString(), request.get("password").toString()) {
                    result_code, user ->
                    Logger.log(LogLevel.DEBUG,"Received result after processing by Users interactor. " +
                            "Remote IP: $sessionIP,Result: $result_code", "LoginController","login_user.exec")
                    if (result_code != Users.UserLoginResultCode.RESULT_OK) {
                        Logger.log(LogLevel.DEBUG,"Received error from Users interactor." +
                                "Remote IP: $sessionIP","LoginController","login_user.exec")
                        status_code = result_code
                    } else {
                        Logger.log(LogLevel.DEBUG,"Received success result from Users interactor. " +
                                "Remote IP: $sessionIP. Preparing response","LoginController","login_user.exec")
                        status = "ok"
                        val sessionObj = MessageCenter.app.sessions.getBy("user_id",user!!["_id"].toString())
                        if (sessionObj != null) {
                            val session = sessionObj as models.Session
                            response.set("session_id",session["_id"])
                        } else {
                            Logger.log(LogLevel.WARNING,"Could not get session_id for user which logged" +
                                    "Remote IP: $sessionIP.","LoginController", "login_user.exec")
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
                            val default_room = MessageCenter.app.rooms.getById(user["default_room"].toString())
                            if (default_room != null) {
                                response.set("default_room",user["default_room"])
                            }
                        }
                        if (user!!["role"]!=null) {
                            response.set("role",user["role"])
                        }
                        val rooms = JSONArray()
                        for (roomObj in MessageCenter.app.rooms) {
                            val room = roomObj as Room
                            val roomDoc = JSONObject()
                            roomDoc.set("_id",room["_id"].toString())
                            roomDoc.set("name",room.get("name").toString())
                            rooms.add(roomDoc)
                        }
                        response.set("rooms",rooms)
                        Logger.log(LogLevel.DEBUG,"Prepared login_user response before process profile image: $response" +
                                "Remote IP: $sessionIP.","LoginController","login_user.exec")
                        if (Files.exists(Paths.get(MessageCenter.app.usersPath+"/"+user["_id"]+"/profile.png"))) {
                            val stream = FileInputStream(MessageCenter.app.usersPath+"/"+user["_id"]+"/profile.png")
                            val img = stream.readBytes()
                            val checksumEngine = CRC32()
                            checksumEngine.update(img)
                            response.set("checksum",checksumEngine.value.toString())
                            response.set("file",img)
                            Logger.log(LogLevel.DEBUG,"Prepared login_user response with profile image " +
                                    "Remote IP: $sessionIP.Response: $response","LoginController","login_user.exec")
                        } else {
                            Logger.log(LogLevel.DEBUG,"Profile image not found for user. " +
                                    "Remote IP: $sessionIP,User body: $user", "LoginController","login_user.exec")
                        }
                    }
                }
            }
            response.set("status",status)
            response.set("status_code",status_code.toString())
            response.set("message",status_code.getMessage())
            Logger.log(LogLevel.DEBUG,"Return final response for login_user request. " +
                    "Remote IP: $sessionIP,Request body: $request, Response body: $response",
                    "LoginController", "login_user.exec")
            return response
        }
    },
    /**
     * Logout user action
     */
    logout_user("logout_user") {
        /**
         * Action executor
         *
         * @param request - request body:
         *                  user_id - Authentication id of user
         *                  session_id - Session id of user
         * @param session - Client websocket session instance
         * @result JSONObject with result of operation:
         *          status - "ok" or "error",status_code="RESULT_OK"
         */
        override fun exec(request: JSONObject, session: Session?): JSONObject {
            Logger.log(LogLevel.DEBUG,"Begin logout_user action for user '$username'." +
                    "Remote IP: $sessionIP.","LoginController","logout_user.exec")
            var result = JSONObject()
            ChatApplication.sessions.remove(request["session_id"].toString(), {})
            result["status"] = "ok"
            result["status_code"] = "RESULT_OK"
            Logger.log(LogLevel.DEBUG,"Finished logout_user action for user '$username' successfully." +
                    "Remote IP: $sessionIP.","LoginController","logout_user.exec")
            return result
        }
        override fun auth(request:JSONObject,session:Session?): JSONObject? {
            Logger.log(LogLevel.DEBUG,"Begin logout_user.auth() for user '$username'." +
                    "Remote IP: $sessionIP.", "LoginController","logout_user.auth")
            return UserController.update_user.auth(request,session)
        }
        override fun before(request:JSONObject,session:Session?): JSONObject {
            var request = super.before(request, session)
            request = UserController.update_user.before(request,session)
            user = request["user"] as models.User
            username = request["username"].toString()
            return request
        }
    };
    override var user: models.User? = null
    override var username: String = ""
    override var sessionIP = ""
    /**
     * Function, which must be executed before any action to check, if request has
     * enough authentication information to use actions of this controller
     *
     * @param request: Request to authenticate
     * @param session: Client WebSocket session instance
     * @return "null" if no authentication error or JSONObject with error description in following fields:
     *         status - "error"
     *         status_code - error code of type Users.UserLoginResultCode
     */
    override open fun auth(request:JSONObject,session:Session?):JSONObject? {
        Logger.log(LogLevel.DEBUG,"Authentication on enter LoginController." +
                "Remote IP: $sessionIP.","LoginController","auth")
        Logger.log(LogLevel.DEBUG,"Authentication on enter LoginController passed successfully." +
                "Remote IP: $sessionIP.","LoginController","auth")
        return null
    }
    companion object {
        /**
         * Function converts string action name, received in WebSocket JSON request
         * to executable LoginAction object
         *
         * @param value String name of action
         * @return LoginController object or null, if incorrect string provided
         */
        fun action(value: String): LoginController? {
            var result: LoginController? = null
            try {
                result = LoginController.valueOf(value)
            } catch (e: Exception) {
            }
            return result
        }
    }
}
