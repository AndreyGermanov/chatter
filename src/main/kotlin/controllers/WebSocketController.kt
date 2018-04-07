/**
 * Created by andrey on 4/7/18.
 */
package controllers

import org.eclipse.jetty.websocket.api.Session
import org.json.simple.JSONObject

/**
 * Interface which should implement each Controller, that processes
 * client requests of WebSocket server
 */
interface WebSocketController {
    /**
     * Action execution method. Each action should have this method
     * @param request Incoming request
     * @param session Link to client WebSocket session instance
     * @return JSONObject with response. Must have "status" field with "ok" or "error" value.
     * Others depend on type of action
     */
    open fun exec(request: JSONObject, session: Session?=null): JSONObject

    /**
     * This method should be called before any action. It should check is request has enough
     * information to be authorized to execute methods of this controller and return information
     * about error as JSONObject, or "null" if no error and request authorized. Only if this method
     * returns "null", request will be passed to action
     *
     * @param request Request to check
     * @param session Link to client WebSocket session instance
     * @return JSONObject with error response, should contain fields "status"="error", "status_code" =
     * MessageCenter.MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR and any other fields, describing
     * error. Or if no error, return "null"
     */
    open fun auth(request:JSONObject,session:Session?=null):JSONObject? {
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
    open fun before(request:JSONObject,session:Session?=null):JSONObject {
        return request
    }

    /**
     * Method, which can be applied to any response, after processing it by action. Can implement various
     * final middlewares. Used to modify response after processing it by action.
     *
     * @param request: Initial request passed through "before" middlewares
     * @param response: Response with action result
     * @param session: Link to client session instance
     * @return Modified response or by default the same response
     */
    open fun after(request:JSONObject,response:JSONObject,session:Session?=null):JSONObject {
        if (request["request_id"]!=null) {
            response["request_id"] = request["request_id"]
        }
        if (request["action"]!=null) {
            response["action"] = request["action"]
        }
        return response
    }
}
