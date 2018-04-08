/**
 * Created by Andrey Germanov on 4/7/18.
 */
package controllers

import core.ChatApplication
import core.MessageCenter
import interactors.Users
import org.eclipse.jetty.websocket.api.Session
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import utils.LogLevel
import utils.Logger
import org.json.simple.parser.JSONParser

/**
 * Set of actions, which can execute only user with ADMIN role
 */
enum class AdminController(val value:String): WebSocketController {
    /**
     * Action used to return list of users as JSON Object
     */
    admin_get_users_list("admin_get_users_list") {
        /**
         * Action executor.
         *
         * @param request: Can contain following optional fields
         *                  fields - which fields to return (JSON Array of field names)
         *                  filter - filtering string for data. (Only fields in "fields" array checked)
         *                  offset - offset from which row to return (after applying filter)
         *                  limit - how many rows to return after applying filter and offset
         * @param session: Link to client WebSocket session instance
         * @return JSONObject with result of operation. Contains following fields:
         *          status - "ok" or "error"
         *          status_code - one of values of "AdminControllerRequestResults" enumeration
         *          field - if error related to one of fields, here this field specified
         *          list - list of users, after applying condition,limit and offset
         *          total_count - total count of items in users collection, before applying condition,limit
         *          and offset
         */
        override open fun exec(request:JSONObject, session:Session?): JSONObject {
            var result = JSONObject()
            var usersArray = JSONArray()
            val username = request["username"].toString();
            Logger.log(LogLevel.DEBUG, "Begin admin_get_users_list action. Username: $username. Remote IP: $sessionIP."+
                    "Request: $request.","AdminController","admin_get_users_list.exec")
            var users = ChatApplication.users.getList().iterator()

            var query = this.prepareListQuery(request,session)
            if (query.containsKey("status") && query["status"] == "error") {
                return query
            }

            usersArray = ChatApplication.users.getListJSON(filter=query["filter"]?.toString() ?: "",
                    fields=query["fields"] as? ArrayList<String> ?: ArrayList<String>(),
                    offset=query["offset"]?.toString()?.toInt() ?: 0,limit=query["limit"]?.toString()?.toInt() ?: 0,
                    sort=query["sort"] as? Pair<String,String> ?: null)
            Logger.log(LogLevel.DEBUG,"Prepared list for admin_get_users_list_request. Username: $username," +
                    "Remote IP: $sessionIP, Request: $request, List: $usersArray")
            if (usersArray.count()>0) {
                result["status"] = "ok"
                result["status_code"] = AdminControllerRequestResults.RESULT_OK
                result["list"] = usersArray
            } else {
                Logger.log(LogLevel.WARNING,"Prepared list for admin_get_users_list_request is empty. " +
                        "Username: $username, Remote IP: $sessionIP, Request: $request")
                result["status"] = "error"
                result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_EMPTY_RESULT
            }
            return result
        }
    },
    admin_get_user("admin_get_user"),
    admin_update_user("admin_update_user"),
    admin_remove_user("admin_remove_user");

    override var user: models.User? = null
    override var username: String = ""
    override var sessionIP = ""

    override open fun exec(request: JSONObject, session: Session?): JSONObject {
        return JSONObject()
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
    override open fun auth(request: JSONObject, session: Session?): JSONObject? {
        Logger.log(LogLevel.DEBUG, "Authentication on enter AdminController started." +
                "Remote IP: $sessionIP", "AdminController", "auth")
        var response: JSONObject? = UserController.update_user.auth(request, session)
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
                "Username: ${user["login"].toString()},Remote IP: $sessionIP.", "AdminController", "auth")
        return null
    }
    override open fun before(request: JSONObject, session: Session?): JSONObject {
        val request = UserController.update_user.before(request,session)
        return request
    }

    /**
     * Function used to validate and prepare filtering and sorting options for functions
     * which is getting lists of items (like lists of users, sessions, messages). Function can
     * either return validation error, or prepared query to pass to underlying function getListJSON() of
     * underlying DB collection
     *
     * @param request - Request, which can contain fields "limit", "offset", "filter", "fields" and "sort"
     * @param session - Link to Client WebSocket session
     * @return Depends on validation result. If failed validation, returns standard error response with fields
     * "status" = "error", "status_code" - AdminControllerResultCode and "field" = (limit,offset,filter,fields,sort).
     * If passed validation, returns prepared JSON object with params, prepared to pass to underlying query function.
     * Parms of successful result object are: limit,offset,filter,fields,sort
     */
    open fun prepareListQuery(request:JSONObject,session:Session?): JSONObject{
        var query = JSONObject()
        var result = JSONObject()
        var fields = ArrayList<String>()
        var parser = JSONParser()
        if (request.containsKey("fields")) {
            var parseFieldsError = false;
            if (request["fields"] is String) {
                try {
                    fields = parser.parse(request["fields"].toString()) as ArrayList<String>
                } catch (e: Exception) {
                    parseFieldsError = true
                }
            } else if (request["fields"] is ArrayList<*>) {
                try {
                    fields = request["fields"] as ArrayList<String>
                } catch (e: Exception) {
                    parseFieldsError = true;
                }
            } else {
                parseFieldsError = true;
            }
            if (parseFieldsError) {
                Logger.log(LogLevel.WARNING, "Could not parse 'fields' field of request to JSON." +
                        "Username: $username,Remote IP: $sessionIP, Request: $request",
                        "AdminController","admin_get_users_list.exec")
                result["status"] = "error"
                result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                result["field"] = "fields"
                return result
            }
            if (request["fields"].toString().isEmpty() || fields.count()==0) {
                Logger.log(LogLevel.WARNING,"Field list is empty. Nothing to return." +
                        "Username: $username,Remote IP: $sessionIP, Request: $request",
                        "AdminController","admin_get_users_list.exec")
                result["status"] = "error"
                result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
                result["field"] = "fields"
                return result
            }
            query["fields"] = fields
        }
        if (request.containsKey("filter") && request["filter"].toString().trim().count()==0) {
            Logger.log(LogLevel.WARNING,"Filter is empty. Either not provide filter, or provide any value in it" +
                    "Username: $username,Remote IP: $sessionIP, Request: $request",
                    "AdminController","admin_get_users_list.exec")
            result["status"] = "error"
            result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
            result["field"] = "filter"
            return result
        }
        query["filter"] = request["filter"].toString().trim()
        var offset = 0
        if (request.containsKey("offset")) {
            var offsetFailure = false
            if (request["offset"].toString().trim().count()==0) {
                Logger.log(LogLevel.WARNING,"Offset is empty. Offset should be 0 or positive value. Username: " +
                        "$username,Remote IP: $sessionIP, Request: $request",
                        "AdminController","admin_get_users_list.exec")
                offsetFailure = true
                result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
            } else {
                try {
                    offset = request["offset"].toString().trim().toInt()
                } catch (e:Exception) {
                    Logger.log(LogLevel.WARNING,"Incorrect offset. Offset should be 0 or positive value. Username: " +
                            "$username,Remote IP: $sessionIP, Request: $request",
                            "AdminController","admin_get_users_list.exec")
                    offsetFailure = true
                    result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                }
            }
            if (offsetFailure) {
                result["status"] = "error"
                result["field"] = "offset"
                return result
            }
            query["offset"] = offset
        }
        var limit = 0
        if (request.containsKey("limit")) {
            var limitFailure = false
            if (request["limit"].toString().trim().count()==0) {
                Logger.log(LogLevel.WARNING,"Limit is empty. Limit should be positive value. Username: " +
                        "$username,Remote IP: $sessionIP, Request: $request",
                        "AdminController","admin_get_users_list.exec")
                limitFailure = true
                result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
            } else {
                try {
                    limit = request["limit"].toString().trim().toInt()
                } catch (e:Exception) {
                    Logger.log(LogLevel.WARNING,"Incorrect limit. Limit should be positive value. Username: " +
                            "$username,Remote IP: $sessionIP, Request: $request",
                            "AdminController","admin_get_users_list.exec")
                    limitFailure = true
                    result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                }
            }
            if (limit <= 0) {
                Logger.log(LogLevel.WARNING,"Incorrect limit. Limit should be positive value. Username: " +
                        "$username,Remote IP: $sessionIP, Request: $request",
                        "AdminController","admin_get_users_list.exec")
                result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
            }
            if (limitFailure) {
                result["status"] = "error"
                result["field"] = "limit"
                return result
            }
            query["limit"] = limit
        }
        if (request.containsKey("sort")) {
            var failedSort = false;
            var sort = JSONObject()
            var sortPair: Pair<String,String>? = null
            if (request["sort"].toString().isEmpty()) {
                result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
                failedSort = true
                Logger.log(LogLevel.WARNING,"Empty sort field. Username: " +
                        "$username,Remote IP: $sessionIP, Request: $request",
                        "AdminController","admin_get_users_list.exec")
            } else {
                try {
                    sort = parser.parse(request["sort"].toString()) as JSONObject
                } catch (e:Exception) {
                    Logger.log(LogLevel.WARNING, "Could not parse 'sort' field of request to JSON." +
                            "Username: $username,Remote IP: $sessionIP, Request: $request",
                            "AdminController","admin_get_users_list.exec")
                    failedSort = true
                    result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                }
                if (sort.count()==0) {
                    Logger.log(LogLevel.WARNING,"Incorrect sort field. Username: " +
                            "$username,Remote IP: $sessionIP, Request: $request",
                            "AdminController","admin_get_users_list.exec")
                    failedSort = true
                } else {
                    for ((index,value) in sort) {
                        if (value.toString() != "ASC" && value.toString()!="DESC") {
                            failedSort = true;
                            result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                        } else if (!ChatApplication.users.schema.containsKey(index.toString())) {
                            failedSort = true;
                            result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                        } else {
                            sortPair = index.toString() to value.toString()
                        }
                    }
                }
                if (failedSort) {
                    result["status"] = "error"
                    result["field"] = "sort"
                    return result
                }
                if (sortPair!=null) {
                    query["sort"] = sortPair!!
                }
            }
        }
        return query
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
    }
}

/**
 * Definitions onf result codes for Admin controller operations, which returned to client
 * with request results
 */
enum class AdminControllerRequestResults(value:String) {
    RESULT_OK("RESULT_OK"),
    RESULT_ERROR_FIELD_IS_EMPTY("RESULT_ERROR_FIELD_IS_EMPTY"),
    RESULT_ERROR_INCORRECT_FIELD_VALUE("RESULT_ERROR_INCORRECT_FIELD_VALUE"),
    RESULT_ERROR_EMPTY_RESULT("RESULT_ERROR_EMPTY_RESUT");
    fun getMessage():String {
        var result = ""
        when(this) {
            RESULT_OK -> result = ""
            RESULT_ERROR_FIELD_IS_EMPTY -> result = "Field is empty"
            RESULT_ERROR_INCORRECT_FIELD_VALUE -> result = "Incorrect field value"
            RESULT_ERROR_EMPTY_RESULT -> result = "Result is empty"
        }
        return result
    }
}

