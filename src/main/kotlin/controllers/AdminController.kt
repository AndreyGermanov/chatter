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
        override fun exec(request:JSONObject, session:Session?): JSONObject {
            var result = JSONObject()
            var usersArray = JSONArray()
            val username = request["username"].toString();
            Logger.log(LogLevel.DEBUG, "Begin admin_get_users_list action. Username: $username. " +
                    "Remote IP: $sessionIP. Request: $request.","AdminController","admin_get_users_list.exec")
            var query = this.prepareListQuery(request,session)
            if (query.containsKey("status") && query["status"] == "error") {
                return query
            }
            Logger.log(LogLevel.DEBUG,"Prepared query to users collection: $query. Username: $username," +
                    "Remote IP: $sessionIP, request: $request","AdminController","admin_get_users_list.exec")
            usersArray = ChatApplication.users.getListJSON(query)
            Logger.log(LogLevel.DEBUG,"Prepared list for admin_get_users_list request. Username: $username," +
                    "Remote IP: $sessionIP, Request: $request, List: $usersArray","AdminController",
                    "admin_get_users_list.exec")
            if (usersArray.count()>=0) {
                result["status"] = "ok"
                result["status_code"] = AdminControllerRequestResults.RESULT_OK
                result["list"] = usersArray
            }
            return result
        }
    },
    admin_get_user("admin_get_user"),
    admin_update_user("admin_update_user"),
    admin_remove_users("admin_remove_users") {
        override fun exec(request:JSONObject, session:Session?): JSONObject {
            Logger.log(LogLevel.DEBUG, "Begin admin_remove_users action. Username: $username. " +
                    "Remote IP: $sessionIP. Request: $request.","AdminController","admin_get_users_list.exec")
            val response = JSONObject()
            val parser = JSONParser()
            if (request["list"]==null) {
                response["status"] = "error"
                response["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
                response["field"] = "list"
                Logger.log(LogLevel.WARNING,"admin_remove_users action requires 'list argument, but it's empty." +
                        "Username: $username,Remote IP: $sessionIP,Request:$request","AdminController",
                        "admin_remove_users.exec")
                return response
            }
            var list:JSONArray? = null
            if (request["list"] is String) {
                try {
                    list = parser.parse(request["list"].toString()) as JSONArray
                } catch (e:Exception) {
                    response["status"] = "error"
                    response["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                    response["field"] = "list"
                    Logger.log(LogLevel.WARNING,"Could not parse list of users to remove from ${request["list"]}." +
                            "Username: $username,Remote IP: $sessionIP,Request:$request","AdminController",
                            "admin_remove_users.exec")
                }
            } else if (request["list"] is JSONArray) {
                list = request["list"] as JSONArray
            }
            if (list==null || list.count()==0) {
                response["status"] = "error"
                response["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
                response["field"] = "list"
                Logger.log(LogLevel.WARNING,"admin_remove_users action requires 'list argument, but it's empty." +
                        "Username: $username,Remote IP: $sessionIP,Request:$request","AdminController",
                        "admin_remove_users.exec")
                return response
            }
            val user_ids = ArrayList<String>()
            val it = list.iterator()
            while (it.hasNext()) {
                val user_id = it.next() as? String
                if (user_id!=null && user_id != request["user_id"].toString()) {
                    user_ids.add(user_id)
                } else if (user_id == request["user_id"].toString()) {
                    Logger.log(LogLevel.WARNING,"One of user_ids passed to action belongs to originator of request." +
                            "Sorry, you can not delete yourself. Your ID filtered from list. Username: $username," +
                            "Remote IP: $sessionIP,Request:$request")
                }
            }
            if (user_ids.count()==0) {
                response["status"] = "error"
                response["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
                response["field"] = "list"
                Logger.log(LogLevel.WARNING,"admin_remove_users action requires 'list argument, but it's empty." +
                        "Username: $username,Remote IP: $sessionIP,Request:$request","AdminController",
                        "admin_remove_users.exec")
                return response
            }
            Logger.log(LogLevel.DEBUG,"Prepared list of users to remove: $user_ids. Removing ...Username: $username," +
                    "Remote IP: $sessionIP,Request:$request","AdminController","admin_remove_users.exec")
            val removed_count = ChatApplication.users.removeUsers(user_ids)
            if (removed_count == 0) {
                Logger.log(LogLevel.WARNING,"Did not remove any user. Probable ids are incorrect" +
                        "Username: $username,Remote IP: $sessionIP,Request:$request","AdminController",
                        "admin_remove_users.exec")
            } else {
                Logger.log(LogLevel.DEBUG,"Successfuly removed $removed_count items. Username: $username," +
                        "Remote IP: $sessionIP,Request:$request","AdminController","admin_remove_users.exec")
            }
            response["status"] = "ok"
            response["status_code"] = AdminControllerRequestResults.RESULT_OK
            response["count"] = removed_count
            return response
        }
    };

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
            return response
        }
        response = JSONObject(hashMapOf(
                "status_code" to MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR,
                "status" to "error",
                "message" to MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR.getMessage()
        ))
        val user = MessageCenter.app.users.getById(request.get("user_id").toString()) as models.User
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
        return UserController.update_user.before(request,session)
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
    open fun prepareListQuery(request:JSONObject,session:Session?): JSONObject {
        val query = JSONObject()
        val result = JSONObject()
        var fields = ArrayList<String>()
        val parser = JSONParser()
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
                        "AdminController","prepareListQuery")
                result["status"] = "error"
                result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                result["field"] = "fields"
                return result
            }
            if (request["fields"].toString().isEmpty() || fields.count()==0) {
                Logger.log(LogLevel.WARNING,"Field list is empty. Nothing to return." +
                        "Username: $username,Remote IP: $sessionIP, Request: $request",
                        "AdminController","prepareListQuery")
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
                    "AdminController","prepareListQuery")
            result["status"] = "error"
            result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
            result["field"] = "filter"
            return result
        } else if (request.containsKey("filter")) {
            query["filter"] = request["filter"].toString().trim()
        }
        var offset = 0
        if (request.containsKey("offset")) {
            var offsetFailure = false
            if (request["offset"].toString().trim().count()==0) {
                Logger.log(LogLevel.WARNING,"Offset is empty. Offset should be 0 or positive value. Username: " +
                        "$username,Remote IP: $sessionIP, Request: $request",
                        "AdminController","prepareListQuery")
                offsetFailure = true
                result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
            } else {
                try {
                    offset = request["offset"].toString().trim().toInt()
                } catch (e:Exception) {
                    Logger.log(LogLevel.WARNING,"Incorrect offset. Offset should be 0 or positive value. Username: " +
                            "$username,Remote IP: $sessionIP, Request: $request",
                            "AdminController","prepareListQuery")
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
                        "AdminController","prepareListQuery")
                limitFailure = true
                result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
            } else {
                try {
                    limit = request["limit"].toString().trim().toInt()
                } catch (e:Exception) {
                    Logger.log(LogLevel.WARNING,"Incorrect limit. Limit should be positive value. Username: " +
                            "$username,Remote IP: $sessionIP, Request: $request",
                            "AdminController","prepareListQuery")
                    limitFailure = true
                    result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                }
            }
            if (limit <= 0) {
                Logger.log(LogLevel.WARNING,"Incorrect limit. Limit should be positive value. Username: " +
                        "$username,Remote IP: $sessionIP, Request: $request",
                        "AdminController","prepareListQuery")
                result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
            }
            if (limitFailure) {
                result["status"] = "error"
                result["field"] = "limit"
                return result
            }
            query["limit"] = limit
        }
        var failedSort = false
        if (request.containsKey("sort")) {
            var sort = JSONObject()
            var sortPair: Pair<String,String>? = null
            if (request["sort"].toString().isEmpty()) {
                result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
                failedSort = true
                Logger.log(LogLevel.WARNING,"Empty sort field. Username: " +
                        "$username,Remote IP: $sessionIP, Request: $request",
                        "AdminController","prepareListQuery")
            } else {
                try {
                    sort = parser.parse(request["sort"].toString()) as JSONObject
                } catch (e:Exception) {
                    Logger.log(LogLevel.WARNING, "Could not parse 'sort' field of request to JSON." +
                            "Username: $username,Remote IP: $sessionIP, Request: $request",
                            "AdminController","prepareListQuery")
                    failedSort = true
                    result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                }
                if (sort.count()==0) {
                    Logger.log(LogLevel.WARNING,"Incorrect sort field. Username: " +
                            "$username,Remote IP: $sessionIP, Request: $request",
                            "AdminController","prepareListQuery")
                    failedSort = true
                } else {
                    for ((index,value) in sort) {
                        if (value.toString() != "ASC" && value.toString()!="DESC") {
                            failedSort = true;
                            Logger.log(LogLevel.WARNING,"Incorrect sort field direction - ${value.toString()}. " +
                                    "Should be ASC or DESC. Username: $username,Remote IP: $sessionIP, Request: " +
                                    "$request","AdminController","prepareListQuery")
                            result["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                        } else {
                            sortPair = index.toString() to value.toString()
                        }
                    }
                }
                if (sortPair!=null) {
                    query["sort"] = sortPair
                }
            }
        }
        if (failedSort) {
            result["status"] = "error"
            result["field"] = "sort"
            return result
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
 * Definitions of result codes for Admin controller operations, which returned to client
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

