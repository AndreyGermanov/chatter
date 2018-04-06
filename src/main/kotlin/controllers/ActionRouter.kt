/**
 * Created by Andrey Germanov on 4/7/18.
 */
package controllers

import core.MessageCenter
import org.eclipse.jetty.websocket.api.Session
import org.json.simple.JSONObject
import utils.LogLevel
import utils.Logger

/**
 * Singleton object used to route incoming action to appropriate controller, execute aciton executor of controller
 * and return result
 *
 * @param request: Received request
 * @param session: Client WebSocket session instance
 * @return JSONObject with response fields depending on action
 */
object ActionRouter {
    /**
     * Function used to find appropriate controller and action, to process incoming request, to execute
     * this handler and return result as JSONObject
     *
     * @param request: Incoming request
     * @param session: Client WebSocket session instance
     * @return JSONObject with response to request
     */
    fun processAction(request: JSONObject, session: Session?):JSONObject {
        Logger.log(LogLevel.DEBUG,"Begin routing request to Controller action. " +
                "Remote IP: ${session?.remote?.inetSocketAddress?.hostName}" +
                "Request: $request", "ActionRouter","processAction")
        val system_error_response = JSONObject()
        system_error_response.set("status","error")
        system_error_response.set("status_code", MessageCenter.MessageObjectResponseCodes.INTERNAL_ERROR)
        system_error_response.set("message", MessageCenter.MessageObjectResponseCodes.INTERNAL_ERROR.getMessage())
        if (!request.containsKey("request_id") || !request.get("request_id").toString().isEmpty()) {
            Logger.log(LogLevel.WARNING,"Received request with incorrect request_id. " +
                    "Remote IP: ${session?.remote?.inetSocketAddress?.hostName}" +
                    "Request : $request", "ActionRouter","processAction")
            return system_error_response
        }
        var action = request["action"].toString()
        if (LoginController.action(action)!=null) {
            return LoginController.auth(request,session) ?: LoginController.action(action)!!.exec(request,session)
        } else if (UserController.action(action)!=null) {
            return UserController.auth(request,session) ?: UserController.action(action)!!.exec(request,session)
        } else if (AdminController.action(action)!=null) {
            return AdminController.auth(request, session) ?: AdminController.action(action)!!.exec(request, session)
        } else {
            Logger.log(LogLevel.WARNING,"Could not find controller and handler for provided action '$action'. " +
                    "Remote IP: ${session?.remote?.inetSocketAddress?.hostName}" +
                    "Request: $request", "ActionRouter","processAction")
            return system_error_response
        }
    }
}

