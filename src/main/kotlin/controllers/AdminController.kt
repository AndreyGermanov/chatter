/**
 * Created by Andrey Germanov on 4/7/18.
 */
package controllers

import core.MessageCenter
import interactors.Users
import org.eclipse.jetty.websocket.api.Session
import org.json.simple.JSONObject
import utils.LogLevel
import utils.Logger

/**
 * Set of actions, which can execute only user with ADMIN role
 */
enum class AdminController(val value:String) {
    admin_get_users_list("admin_get_users_list"),
    admin_get_user("admin_get_user"),
    admin_update_user("admin_update_user"),
    admin_remove_user("admin_remove_user");
    open fun exec(request: JSONObject, session: Session?=null): JSONObject {
        return JSONObject()
    }
    companion object {
        /**
         * Function converts string action name, received in WebSocket JSON request
         * to executable UserAction object
         *
         * @param value String name of action
         * @return AdminController object or null, if incorrect string provided
         */
        fun action(value: String): AdminController? {
            var result: AdminController? = null
            try {
                result = AdminController.valueOf(value)
            } catch (e: Exception) {
            }
            return result
        }

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
        fun auth(request: JSONObject, session: Session? = null): JSONObject? {
            Logger.log(LogLevel.DEBUG, "Authentication on enter AdminController started." +
                    "Remote IP: ${session?.remote?.inetSocketAddress?.hostName ?: ""}", "AdminController", "auth")
            var response: JSONObject? = UserController.auth(request, session)
            if (response != null) {
                return response;
            }
            response = JSONObject(hashMapOf(
                    "status" to "error",
                    "status_code" to MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR,
                    "message" to MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR.getMessage()
            ))
            var user = MessageCenter.app.users.getById(request.get("user_id").toString()) as models.User
            if (user["role"] == null) {
                return response
            }
            var role = Users.UserRole.USER
            try {
                role = Users.UserRole.getValueByCode(user["role"].toString().toInt())
            } catch (e: Exception) {
            }
            if (role != Users.UserRole.ADMIN) {
                return response
            }
            Logger.log(LogLevel.DEBUG, "Authentication on enter AdminController finished successfully." +
                    "Remote IP: ${session?.remote?.inetSocketAddress?.hostName ?: ""}", "AdminController", "auth")
            return null
        }
    }
}