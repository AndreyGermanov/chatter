/**
 * Created by Andrey Germanov on 4/6/18.
 */
package controllers

import core.ChatApplication
import core.MessageCenter
import interactors.Users
import models.User
import org.eclipse.jetty.websocket.api.Session
import org.json.simple.JSONObject
import utils.LogLevel
import utils.Logger
import java.util.HashMap

/**
 * User Controller - set of actions, which authorized client can execute via WebSocket server
 */
enum class UserController(val value:String): WebSocketController {
    /**
     * Update user profile action. Updates user profile information (login, email, first_name,
     * profile image etc.)
     */
    update_user("update_user") {
        /**
         * Action executor
         *
         * @param request - JSON object with authentication params and params to update:
         *                  Required:
         *                  user_id - user_id used for authenticate user
         *                  session_id - session Id used to authentcate user
         *                  request_id - unique ID of request
         *                  profile_image_checksum - Checksum of profile image, which sent in separate binary message
         *                  login - Login of user
         *                  password - Password of user
         *                  confirm_password - Confirm password of user
         *                  first_name - First name of user
         *                  last_name - Last name of user
         *                  gender - Gender of user: "M" or "F"
         *                  birthDate - Date of Birth of user as integer timestamp
         *                  default_room - ID of default room of user
         *                  role - numeric code of Users.UserRole enumeration
         * @param session - Client WebSocket session instance
         * @return - JSON object with result of operation:
         *           status - "ok" or "error"
         *           status_code - status code. Item of enumeration Users.UserUpdateResultCode
         *           message - text representation of error
         *           field - if returned error related to one of fields, contains string name of this field
         */
         override open fun exec(request: JSONObject, session: Session?): JSONObject {
            val result = JSONObject()
            var status = "error"
            var status_code = Users.UserUpdateResultCode.RESULT_OK
            var message = ""
            var field = ""
            Logger.log(LogLevel.DEBUG,"Begin processing update_user request. " +
                    "Username: $username, Remote IP: $sessionIP, Request body: $request",
                    "UserController","update_user.exec")
            MessageCenter.app.users.updateUser(request) { result_code, msg ->
                Logger.log(LogLevel.DEBUG,"Receive response from Users interactor: $result_code,'$msg' " +
                        "Username: $username,Remote IP: $sessionIP.","UserController","update_user.exec")
                status_code = result_code
                if (status_code != Users.UserUpdateResultCode.RESULT_OK) {
                    Logger.log(LogLevel.DEBUG,"Received error response from Users interactor: $result_code, '$msg' " +
                            "Username: $username, Remote IP: $sessionIP.","UserController","update_user.exec")
                    if (status_code == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY ||
                            status_code == Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE) {
                        field = msg
                        message = status_code.getMessage()+ " " + field
                    } else {
                        message = status_code.getMessage()
                    }
                } else {
                    if (request.contains("profile_image_checksum") &&
                            request.get("profile_image_checksum").toString().isEmpty()) {
                        Logger.log(LogLevel.WARNING,"Received error response from Users interactor " +
                                "(empty profile_image_checksum)," +
                                "Username: $username, Remote IP: $sessionIP","UserController","update_user.exec")
                        status_code = Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY
                        field = "profile_image_checksum"
                        message = "Error with profile image. Please, try again"
                    } else {
                        status = "ok"
                        var checksum: Long = 0
                        if (request.contains("profile_image_checksum")) {
                            try {
                                checksum = request.get("profile_image_checksum").toString().toLong()
                            } catch (e: Exception) {
                                Logger.log(LogLevel.DEBUG,"Received successful error response from Users interactor " +
                                        "(incorrect profile_image_checksum). Username: $username, " +
                                        "Remote IP: $sessionIP","UserController","update_user.exec")
                                status = "error"
                                field = "profile_image_checksum"
                                status_code = Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE
                                message = "Error with profile image. Please, try again"
                            }
                        }
                        if (checksum>0) {
                            request.set("request_timestamp",System.currentTimeMillis()/1000)
                            val pending_request = HashMap<String,Any>()
                            if (session?.remote!=null) {
                                pending_request.set("session", session)
                            } else {
                                pending_request.set("session","")
                            }
                            pending_request.set("request",request)
                            MessageCenter.file_requests.set(checksum, pending_request)
                            Logger.log(LogLevel.DEBUG,"Add record to 'file_requests' queue for checksum: $checksum. " +
                                    "Username: $username, Remote IP: $sessionIP, " +
                                    "Request body: ${JSONObject(pending_request)}","UserController","update_user.exec")
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
                    "Username: $username, Remote IP: $sessionIP, Request body: $request. Result body: $result",
                    "UserController","update_user.exec")
            return result
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
     *         status_code - error code of type MessageObject.MessageObjectResponseCode
     *         message - string representation of error
     */
     override open fun auth(request:JSONObject,session:Session?):JSONObject? {
        val response = JSONObject()
        val sessionIP = session?.remote?.inetSocketAddress?.address?.toString() ?: ""
        Logger.log(LogLevel.DEBUG,"Authentication on enter UserController started." +
                "Remote IP: $sessionIP.","UserController","auth")
        if (!request.containsKey("user_id") || MessageCenter.app.users.getById(request.get("user_id").toString()) == null) {
            response.set("status","error")
            response.set("status_code", MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR)
            response.set("message", MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR.getMessage())
            return response
        }
        if (!request.containsKey("session_id") || MessageCenter.app.sessions.getById(request.get("session_id").toString()) == null) {
            response.set("status","error")
            response.set("status_code", MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR)
            response.set("message", MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR.getMessage())
            return response
        }
        val user_session = MessageCenter.app.sessions.getById(request.get("session_id").toString()) as models.Session
        if (user_session["user_id"] != request.get("user_id").toString()) {
            response.set("status","error")
            response.set("status_code", MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR)
            response.set("message", MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR.getMessage())
            return response
        }
        Logger.log(LogLevel.DEBUG,"Authentication on enter UserController passed successfully." +
                "Remote IP: $sessionIP.","UserController","auth")
        return null
    }

    /**
     * Method, which can be applied to request before passing it to action. Can implement various middlewares,
     * which extends, modifies request for all actions of controllers.
     *
     * @param request : Initial request to modify
     * @param session: Link to client session instance
     * @return Modified request
     */
    override open fun before(request:JSONObject,session:Session?):JSONObject {
        val request = super.before(request, session)
        Logger.log(LogLevel.DEBUG,"Starting 'before' handler." +
                "Remote IP: $sessionIP.","UserController","before")
        val user = ChatApplication.users.getById(request["user_id"]!!.toString())!! as User
        request["username"] = user["login"].toString()
        request["user"] = user
        this.username = request["username"].toString()
        this.user = user
        Logger.log(LogLevel.DEBUG,"Finish 'before' handler.Username: $username, Remote IP: $sessionIP.",
                "UserController","before")
        return request
    }
    companion object {
        /**
         * Function converts string action name, received in WebSocket JSON request
         * to executable UserAction object
         *
         * @param value String name of action
         * @return UserController object or null, if incorrect string provided
         */
        fun action(value: String): UserController? {
            var result: UserController? = null
            try {
                result = UserController.valueOf(value)
            } catch (e: Exception) {
            }
            return result
        }
    }
}



