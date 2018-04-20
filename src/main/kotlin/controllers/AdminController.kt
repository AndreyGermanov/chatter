/**
 * Created by Andrey Germanov on 4/7/18.
 */
package controllers

import core.ChatApplication
import core.MessageCenter
import interactors.Users
import models.DBModel
import models.User
import org.bson.Document
import org.eclipse.jetty.websocket.api.Session
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import utils.*

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
            val response = JSONObject()
            val username = request["username"].toString()
            Logger.log(LogLevel.DEBUG, "Begin admin_get_users_list action. Username: $username. " +
                    "Remote IP: $sessionIP. Request: $request.","AdminController","admin_get_users_list.exec")
            val query = this.prepareListQuery(request,session)
            if (query.containsKey("status") && query["status"] == "error") {
                return query
            }
            Logger.log(LogLevel.DEBUG,"Prepared query to users collection: $query. Username: $username," +
                    "Remote IP: $sessionIP, request: $request","AdminController","admin_get_users_list.exec")
            val usersArray = ChatApplication.users.getListJSON(query)
            Logger.log(LogLevel.DEBUG,"Prepared list for admin_get_users_list request. Username: $username," +
                    "Remote IP: $sessionIP, Request: $request, List: $usersArray","AdminController",
                    "admin_get_users_list.exec")
            if (usersArray.count()>=0) {
                response["status"] = "ok"
                response["status_code"] = AdminControllerRequestResults.RESULT_OK
                response["list"] = usersArray
            }
            return response
        }
    },
    /**
     * Action used to get single user record by field name or value
     */
    admin_get_user("admin_get_user") {
        /**
         * Action executor
         *
         * @param request: Request body. Should contain "query" param, which is JSONObject in form of
         * {field_name:field_value} which identifies, which user to return. Only first user which meet this
         * condition returned
         * @param session: Link to WebSocket client session instance
         * @return response, with results of operation. Contains fields:
         *  'status' = "error" in case of error, and "ok" in case if "ok"
         *  'status_code' - description of error as AdminControllerRequestResults enumeration value
         *  'user' - if success, here is JSONObject with user data
         */
        override fun exec(request:JSONObject,session:Session?):JSONObject {
            val logInfo = "Username: $username,Remote IP: $sessionIP. Request: $request"
            Logger.log(LogLevel.DEBUG,"Begin admin_get_user_request. " +
                    logInfo, "AdminController","admin_get_user.exec")
            val response = JSONObject()
            val condition_json = toJSONObject(request["query"])
            response["status"] = "error"
            response["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
            response["field"] = "query"
            if (condition_json == null) {
                Logger.log(LogLevel.WARNING,"Could not parse 'query' to JSON. Query: ${request["query"]}. $logInfo",
                        "AdminController","admin_get_user.exec")
                return response
            }
            val condition = JSONToPair(condition_json)
            if (condition == null) {
                Logger.log(LogLevel.WARNING,"Incorrect query format. Query: $condition_json. $logInfo",
                        "AdminController","admin_get_user.exec")
                return response
            }
            if (!ChatApplication.users.schema.containsKey(condition.first)) {
                Logger.log(LogLevel.WARNING,"Incorrect field name in query. Field: ${condition.first}. " +
                        "Query: $condition_json. $logInfo", "AdminController","admin_get_user.exec")
                response["field"] = condition.first
                return response
            }
            val user = ChatApplication.users.getBy(condition.first,condition.second)
            if (user == null || user !is User) {
                Logger.log(LogLevel.WARNING,"User not found. Condition: ${condition}. " +
                        "Query: $condition_json. $logInfo", "AdminController","admin_get_user.exec")
                response["status_code"] = AdminControllerRequestResults.RESULT_ERROR_OBJECT_NOT_FOUND
                return response
            }
            response["status"] = "ok"
            response["status_code"] = AdminControllerRequestResults.RESULT_OK
            val user_json = user.toJSON()
            if (user_json.containsKey("password")) {
                user_json.remove("password")
            }
            response["user"] = user_json
            Logger.log(LogLevel.DEBUG,"admin_get_user action finished successfully. User: ${response["user"]}. " +
                    "Query: $condition_json. $logInfo", "AdminController","admin_get_user.exec")
            return response
        }
    },
    /**
     * Action used to create new user.
     */
    admin_add_user("admin_add_user") {
        /**
         * Action executor which adds new user to database and to Users collection
         *
         * @param request: Request object which must contain "fields" JSONArray, each of items is JSONObject
         * in format {"field_name":"field_value"}.
         * @return result of operation as objec with fields:
         *          status - result of operation: "ok" or "error"
         *          status_code - RESULT_OK or error code as AdminController enumeration request result
         *          user - added user record in case of success
         */
        override fun exec(request:JSONObject,session:Session?):JSONObject {
            val logInfo = "Username: $username,Remote IP: $sessionIP. Request: $request"
            Logger.log(LogLevel.DEBUG,"Begin admin_add_user request handler. $logInfo",
                    "AdminController","admin_add_user.exec")
            val response = JSONObject()
            val fields:JSONArray?
            val parse_response = parseJSONArrayFromRequest(request,"fields",session)
            if (parse_response["status"].toString() == "ok") {
                fields = parse_response["result"] as JSONArray
            } else {
                Logger.log(LogLevel.DEBUG,"Error parsing fields to JSON: ${request["fields"]}. $logInfo",
                        "AdminController","admin_add_user.exec")
                return parse_response
            }
            Logger.log(LogLevel.DEBUG,"Begin fields validation for fields: $fields. $logInfo",
                    "AdminController","admin_add_user.exec")
            val result = this.validateFields(fields)
            if (result is JSONObject) {
                Logger.log(LogLevel.WARNING,"Errors during fields validation: $fields. $logInfo",
                        "AdminController","admin_add_user.exec")
                return result
            } else if (result is User ){
                Logger.log(LogLevel.DEBUG,"Fields validated successfully. Returned object: $user. $logInfo",
                        "AdminController","admin_add_user.exec")
                result.save{}
                ChatApplication.users.addModel(result)
                response["status"] = "ok"
                response["status_code"] = AdminControllerRequestResults.RESULT_OK
                val user_obj = result.toJSON()
                Logger.log(LogLevel.DEBUG,"Prepared JSON object from user model to return: $user_obj. $logInfo",
                        "AdminController","admin_add_user.exec")
                if (user_obj.containsKey("password")) {
                    user_obj.remove("password")
                }
                response["user"] = user_obj
            }
            return response
        }
    },
    /**
     * Action used to update user properties
     */
    admin_update_user("admin_update_user") {
        /**
         * Action executor which updates existing user in database and in Users collection
         *
         * @param request: Request object which must contain "fields" JSONArray, each of items is JSONObject
         * in format {"field_name":"field_value"} and "id" property, which identifies id of user which need to update
         * @return result of operation as objec with fields:
         *          status - result of operation: "ok" or "error"
         *          status_code - RESULT_OK or error code as AdminController enumeration request result
         *          user - added user record in case of success
         */
        override fun exec(request:JSONObject,session:Session?):JSONObject {
            val logInfo = "Username: $username,Remote IP: $sessionIP. Request: $request"
            Logger.log(LogLevel.DEBUG,"Begin admin_update_user request handler. $logInfo",
                    "AdminController","admin_update_user.exec")
            val response = JSONObject()
            val fields:JSONArray?
            val parse_response = parseJSONArrayFromRequest(request,"fields",session)
            if (parse_response["status"].toString() == "ok") {
                fields = parse_response["result"] as JSONArray
            } else {
                Logger.log(LogLevel.WARNING,"Error parsing fields to JSON: ${request["fields"]}. $logInfo",
                        "AdminController","admin_update_user.exec")
                return parse_response
            }
            var user_id:String
            if (!request.containsKey("id") || request["id"]==null) {
                Logger.log(LogLevel.WARNING,"User to modify 'id' not specified $logInfo",
                        "AdminController","admin_update_user.exec")
                response["status"] = "error"
                response["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
                response["field"] = "id"
                return response
            }
            user_id = request["id"].toString()
            Logger.log(LogLevel.DEBUG,"Begin fields validation for fields: $fields. $logInfo",
                    "AdminController","admin_update_user.exec")
            val result = this.validateFields(fields,user_id)
            if (result is JSONObject) {
                Logger.log(LogLevel.WARNING,"Errors during fields validation: $fields. $logInfo",
                        "AdminController","admin_update_user.exec")
                return result
            } else if (result is User) {
                Logger.log(LogLevel.DEBUG, "Fields validated successfully. Returned object: $user. $logInfo",
                        "AdminController", "admin_update_user.exec")
                result.save {}
                (ChatApplication.users.getById(user_id) as User).doc = result.doc
                response["status"] = "ok"
                response["status_code"] = AdminControllerRequestResults.RESULT_OK
                val user_obj = result.toJSON()
                Logger.log(LogLevel.DEBUG,"Prepared JSON object from user model to return: $user_obj. $logInfo",
                        "AdminController","admin_update_user.exec")
                if (user_obj.containsKey("password")) {
                    user_obj.remove("password")
                }
                response["user"] = user_obj
            }
            return response
        }
    },
    /**
     * Action used to remove users from database
     */
    admin_remove_users("admin_remove_users") {
        /**
         * Action executor
         *
         * @param request - User remove request. should contain "list" of user_id items to remove as JSON Array
         * @param session - Link to Client WebSocket session instance
         * @return JSON object with either error or success information. Error includes "status_code" with information
         * about error. Success includes "count" field, which contains actual number of removed items.
         */
        override fun exec(request:JSONObject, session:Session?): JSONObject {
            val logInfo =  "Username: $username. Remote IP: $sessionIP. Request: $request"
            Logger.log(LogLevel.DEBUG, "Begin admin_remove_users action. $logInfo",
                    "AdminController","admin_remove_users.exec")
            val response = JSONObject()
            if (request["list"]==null) {
                response["status"] = "error"
                response["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
                response["field"] = "list"
                Logger.log(LogLevel.WARNING,"admin_remove_users action requires 'list argument, but it's empty. " +
                        logInfo, "AdminController,","admin_remove_users.exec")
                return response
            }
            val list:JSONArray?
            val parse_response = parseJSONArrayFromRequest(request,"list",session)
            if (parse_response["status"].toString() == "ok") {
                list = parse_response["result"] as JSONArray
            } else {
                Logger.log(LogLevel.DEBUG,"Error parsing fields to JSON: ${request["fields"]}. $logInfo",
                        "AdminController","admin_remove_users.exec")
                return parse_response
            }
            val user_ids = ArrayList<String>()
            val it = list.iterator()
            while (it.hasNext()) {
                val user_id = it.next() as? String
                if (user_id!=null && user_id != request["user_id"].toString()) {
                    user_ids.add(user_id)
                } else if (user_id == request["user_id"].toString()) {
                    Logger.log(LogLevel.WARNING,"One of user_ids passed to action belongs to originator of request." +
                            "Sorry, you can not delete yourself. Your ID filtered from list. $logInfo",
                            "AdminController","admin_remove_users_.exec")
                }
            }
            if (user_ids.count()==0) {
                response["status"] = "error"
                response["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
                response["field"] = "list"
                Logger.log(LogLevel.WARNING,"admin_remove_users action requires 'list argument, but it's empty. " +
                        logInfo,"AdminController", "admin_remove_users.exec")
                return response
            }
            Logger.log(LogLevel.DEBUG,"Prepared list of users to remove: $user_ids. Removing ... $logInfo",
                    "AdminController","admin_remove_users.exec")
            val removed_count = ChatApplication.users.removeUsers(user_ids)
            if (removed_count == 0) {
                Logger.log(LogLevel.WARNING,"Did not remove any user. Probable ids are incorrect. $logInfo" +
                        "AdminController", "admin_remove_users.exec")
            } else {
                Logger.log(LogLevel.DEBUG,"Successfuly removed $removed_count items. $logInfo",
                        "AdminController","admin_remove_users.exec")
            }
            response["status"] = "ok"
            response["status_code"] = AdminControllerRequestResults.RESULT_OK
            response["count"] = removed_count
            return response
        }
    },
    /**
     * Action used to send activation email to user in any moment of time
     */
    admin_send_activation_email("admin_send_activation_email") {
        /**
         * Action executor. Attempts to send activation email to user and returns result
         * of operation
         *
         * @param request: Client request body which must have "id" field with _id of user to which
         * activation email should be sent
         * @param session: Link to WebSocket client session instance
         * @return Object with result of operation. Consist of 'status' = "error" or "ok" and "status_code" with
         * one of values of UserRegisterResultCode or AdminControllerRequestResults enumeration
         */
        override fun exec(request:JSONObject,session:Session?):JSONObject {
            val logInfo =  "Username: $username. Remote IP: $sessionIP. Request: $request"
            Logger.log(LogLevel.DEBUG, "Begin admin_send_activation_email action. $logInfo",
                    "AdminController","admin_send_activation_email.exec")
            val response = JSONObject()
            response["status"] = "error"
            response["field"] = "id"
            if (!request.containsKey("id") || request["id"] !is String || request["id"].toString().isEmpty()) {
                Logger.log(LogLevel.WARNING, "Field 'id' is empty or not string. id=${request["id"]}. $logInfo",
                        "AdminController","admin_send_activation_email.exec")
                response["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
                return response
            }
            val id = request["id"].toString()
            val user = ChatApplication.users.getById(id)
            if (user==null || user !is User) {
                Logger.log(LogLevel.WARNING, "User not found. id=${request["id"]}. $logInfo",
                        "AdminController","admin_send_activation_email.exec")
                response["status_code"] = AdminControllerRequestResults.RESULT_ERROR_OBJECT_NOT_FOUND
                return response
            }
            val result = ChatApplication.users.sendActivationEmail(user)
            if (!result) {
                Logger.log(LogLevel.WARNING, "Could not send activation email to user: ${user.toJSON()}. $logInfo",
                        "AdminController","admin_send_activation_email.exec")
                response["status_code"] = Users.UserRegisterResultCode.RESULT_ERROR_ACTIVATION_EMAIL
                return response
            }
            response["status"] = "ok"
            response["status_code"] = Users.UserRegisterResultCode.RESULT_OK
            Logger.log(LogLevel.DEBUG, "Activation email send successfully to user: ${user.toJSON()}. $logInfo",
                    "AdminController","admin_send_activation_email.exec")
            return response
        }
    },
    /**
     * Action used to return list of rooms as JSON Object
     */
    admin_get_rooms_list("admin_get_rooms_list") {
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
         *          list - list of rooms, after applying condition,limit and offset
         *          total_count - total count of items in users collection, before applying condition,limit
         *          and offset
         */
        override fun exec(request: JSONObject, session: Session?): JSONObject {
            val response = JSONObject()
            val username = request["username"].toString()
            Logger.log(LogLevel.DEBUG, "Begin admin_get_rooms_list action. Username: $username. " +
                    "Remote IP: $sessionIP. Request: $request.", "AdminController", "admin_get_rooms_list.exec")
            val query = this.prepareListQuery(request, session)
            if (query.containsKey("status") && query["status"] == "error") {
                return query
            }
            Logger.log(LogLevel.DEBUG, "Prepared query to rooms collection: $query. Username: $username," +
                    "Remote IP: $sessionIP, request: $request", "AdminController", "admin_get_rooms_list.exec")
            val roomsArray = ChatApplication.rooms.getListJSON(query)
            Logger.log(LogLevel.DEBUG, "Prepared list for admin_get_rooms_list request. Username: $username," +
                    "Remote IP: $sessionIP, Request: $request, List: $roomsArray", "AdminController",
                    "admin_get_rooms_list.exec")
            if (roomsArray.count() >= 0) {
                response["status"] = "ok"
                response["status_code"] = AdminControllerRequestResults.RESULT_OK
                response["list"] = roomsArray
            }
            return response
        }
    };
    override var user: models.User? = null
    override var username: String = ""
    override var sessionIP = ""
    val parser = JSONParser()

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
     * @param request - Request, which can contain fields "limit", "offset", "filter", "fields", "sort", "get_total",
     * "get_presentations"
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
        if (request.containsKey("get_total")) {
            query["get_total"] = true
        }
        if (request.containsKey("get_presentations")) {
            query["get_presentations"] = true
        }
        if (request.containsKey("fields")) {
            var parseFieldsError = false
            when {
                request["fields"] is String -> try {
                    fields = parser.parse(request["fields"].toString()) as ArrayList<String>
                } catch (e: Exception) {
                    parseFieldsError = true
                }
                request["fields"] is ArrayList<*> -> try {
                    fields = request["fields"] as ArrayList<String>
                } catch (e: Exception) {
                    parseFieldsError = true
                }
                else -> parseFieldsError = true
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
                            failedSort = true
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
    /**
     * Function used to create database model of specified type T and fill it
     * with values from [fields] array. Function implements basic validation
     * using schema of this model and returns object filled only with fields
     * which passed validation
     *
     * @param obj:  Empty model of type T
     * @param fields: Array of fields in format [{key:value},{key:value}]
     * @return model filled with field values after validation
     */
    fun <T: DBModel> createModel(obj:T,fields:JSONArray):JSONObject {
        val model = toJSONObject(obj.doc.toJson())!!
        val logInfo = "Username: $username,Remote IP: $sessionIP."
        val fields_iterator = fields.iterator()
        while (fields_iterator.hasNext()) {
            val field_object = fields_iterator.next()
            val field = toJSONObject(field_object)
            if(field == null || field.count() != 1) {
                Logger.log(LogLevel.WARNING,"Incorrect format of field definition $field_object - $field.  $logInfo",
                        "AdminController","createModel")
                continue
            }
            val field_keys = field.keys.iterator()
            val field_key_obj = field_keys.next()
            if (field_key_obj !is String) {
                Logger.log(LogLevel.WARNING,"Incorrect format of field definition $field. $logInfo",
                        "AdminController","createModel")
                continue
            }
            val field_key = field_key_obj.toString()
            if (!obj.schema.containsKey(field_key) || field_key == "_id") {
                Logger.log(LogLevel.WARNING,"Field '$field_key' does not exist in this model. $logInfo",
                        "AdminController","createModel")
                continue
            }
            val field_type = obj.schema[field_key].toString()
            val field_value_obj = field[field_key]
            var fieldValueError = false
            when (field_type) {
                "Int" -> {
                    try {
                        model[field_key] = field_value_obj.toString().trim().toInt()
                    } catch (e:Exception) {
                        Logger.log(LogLevel.WARNING,"Field '$field_key' is not correct Int. $logInfo",
                                "AdminController","createModel")
                        fieldValueError = true
                    }
                }
                "Double" -> {
                    try {
                        model[field_key] = field_value_obj.toString().trim().toDouble()
                    } catch (e:Exception) {
                        Logger.log(LogLevel.WARNING,"Field '$field_key' is not correct Double. $logInfo",
                                "AdminController","createModel")
                        fieldValueError = true
                    }
                }
                "Boolean" -> {
                    try {
                        model[field_key] = field_value_obj.toString().trim().toBoolean()
                    } catch (e:Exception) {
                        Logger.log(LogLevel.WARNING,"Field '$field_key' is not correct Boolean. $logInfo",
                                "AdminController","createModel")
                        fieldValueError = true
                    }
                }
                else -> {
                    try {
                        model[field_key] = field_value_obj.toString().trim()
                    } catch (e:Exception) {
                        Logger.log(LogLevel.WARNING,"Field '$field_key' is not correct String. $logInfo",
                                "AdminController","createModel")
                        fieldValueError = true
                    }
                }
            }
            if (fieldValueError) {
                continue
            }
        }
        return model
    }
    /** Function used to validate user fields and populate User model with these fields
     * @param fields JSONArray of fields. Each item is JSONObject
     * in format {"field_name":"field_value"}.
     * @param id: ID of model to populate. If not specified, populates new model
     * @return If validated correctly, returns "User" model instance ready to be saved to database,
     * if error, returns JSON object with error status, code and field
     */
    open fun validateFields(fields:JSONArray,id:String?=null):Any {
        val logInfo = "Username: $username,Remote IP: $sessionIP."
        Logger.log(LogLevel.DEBUG,"Validating fields: $fields. " +
                " $logInfo","AdminController","validateFields")
        val error = JSONObject()
        val initial_user:User?
        var initial_login:String? = null
        var initial_email:String? = null
        if (id==null) {
            initial_user = User(ChatApplication.dBServer.db,"users")
        } else {
            initial_user = ChatApplication.users.getById(id) as? User
            if (initial_user == null) {
                Logger.log(LogLevel.WARNING,"User with specified ID $id not found. Fields: $fields. $logInfo",
                        "AdminController","validateFields")
                error["status"] = "error"
                error["status_code"] = AdminControllerRequestResults.RESULT_ERROR_OBJECT_NOT_FOUND
                error["field"] = "id"
                return error
            } else {
                initial_login = initial_user["login"].toString().trim()
                initial_email = initial_user["email"].toString().trim()
            }
        }
        val user = this.createModel(initial_user,fields)
        if (user["login"] == null) {
            Logger.log(LogLevel.WARNING,"'login' field is required. Fields: $fields. $logInfo",
                    "AdminController","validateFields")
            error["status"] = "error"
            error["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
            error["field"] = "login"
            return error
        }
        if (user["email"] == null) {
            Logger.log(LogLevel.WARNING,"'email' field is required. Fields: $fields. $logInfo",
                    "AdminController","validateFields")
            error["status"] = "error"
            error["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
            error["field"] = "email"
            return error

        }
        if (user["default_room"]==null) {
            Logger.log(LogLevel.WARNING,"'default_room' field is required. Fields: $fields. $logInfo",
                    "AdminController","validateFields")
            error["status"] = "error"
            error["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
            error["field"] = "default_room"
            return error
        }
        if (ChatApplication.users.getBy("login",user["login"].toString()) != null) {
            if (initial_login == null || initial_login != user["login"].toString()) {
                Logger.log(LogLevel.WARNING, "Model with provided 'login'=${user["login"]} already exists in database: " +
                        "$fields. $logInfo", "AdminController", "validateFields")
                error["status"] = "error"
                error["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_ALREADY_EXISTS
                error["field"] = "login"
                return error
            }
        }
        if (!isValidEmail(user["email"].toString())) {
            Logger.log(LogLevel.WARNING,"Invalid email format 'email'=${user["email"]} " +
                    "$fields. $logInfo","AdminController","validateFields")
            error["status"] = "error"
            error["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
            error["field"] = "email"
            return error
        }
        if (ChatApplication.users.getBy("email",user["email"].toString()) != null) {
            if (initial_email == null || initial_email != user["email"].toString()) {
                Logger.log(LogLevel.WARNING, "Model with provided 'email'=${user["email"]} already exists in database: " +
                        "$fields. $logInfo", "AdminController", "validateFields")
                error["status"] = "error"
                error["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_ALREADY_EXISTS
                error["field"] = "email"
                return error
            }
        }
        if (ChatApplication.rooms.getById(user["default_room"].toString())==null) {
            Logger.log(LogLevel.WARNING,"Provided default room ='${user["default_room"]}' does not exist." +
                    "$fields. $logInfo","AdminController","validateFields")
            error["status"] = "error"
            error["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
            error["field"] = "default_room"
            return error
        }
        if (user["birthDate"]!=null) {
            val birthDate = user["birthDate"].toString().toInt()
            if (birthDate<0 || birthDate>(System.currentTimeMillis()/1000).toInt()) {
                Logger.log(LogLevel.WARNING,"Incorrect birthDate='$birthDate' " +
                        "$fields. $logInfo","AdminController","validateFields")
                error["status"] = "error"
                error["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                error["field"] = "birthDate"
                return error
            }
        }
        if (user["gender"]!=null) {
            if (user["gender"].toString()!="M" && user["gender"].toString()!="F") {
                Logger.log(LogLevel.WARNING, "Incorrect gender='${user["gender"]}' " +
                        "$fields. $logInfo", "AdminController", "validateFields")
                error["status"] = "error"
                error["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                error["field"] = "gender"
                return error
            }
        }
        if (user["role"]!=null) {
            val role = user["role"].toString().toInt()
            if (role!=1 && role!=2) {
                Logger.log(LogLevel.WARNING,"Incorrect role='${user["role"]}' " +
                        "$fields. $logInfo","AdminController","validateFields")
                error["status"] = "error"
                error["status_code"] = AdminControllerRequestResults.RESULT_ERROR_INCORRECT_FIELD_VALUE
                error["field"] = "role"
                return error
            }
        } else {
            user["role"] = 1
        }
        if (user["password"] != null) {
            val password = user["password"].toString().trim()
            if (password.isEmpty()) {
                Logger.log(LogLevel.WARNING,"Password should not be empty" +
                        "$fields. $logInfo","AdminController","validateFields")
                error["status"] = "error"
                error["status_code"] = AdminControllerRequestResults.RESULT_ERROR_FIELD_IS_EMPTY
                error["field"] = "password"
                return error
            } else {
                user["password"] = BCrypt.hashpw(password, BCrypt.gensalt(12))
            }
        }
        if (user["first_name"] != null) {
            user["first_name"] = user["first_name"].toString().trim().toLowerCase().capitalize()
        }
        if (user["last_name"] != null) {
            user["last_name"] = user["last_name"].toString().trim().toLowerCase().capitalize()
        }
        if (user["active"] == null) {
            user["active"] = false
        }
        Logger.log(LogLevel.DEBUG,"No validation errors during validation. Object constructed: $user. " +
                " $logInfo","AdminController","validateFields")
        val model = User(ChatApplication.dBServer.db,"users")
        model.doc = Document.parse(toJSONString(user))
        return model
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
    RESULT_ERROR_FIELD_ALREADY_EXISTS("RESULT_ERROR_FIELD_ALREADY_EXISTS"),
    RESULT_ERROR_EMPTY_RESULT("RESULT_ERROR_EMPTY_RESUT"),
    RESULT_ERROR_OBJECT_NOT_FOUND("RESULT_ERROR_OBJECT_NOT_FOUND");
    fun getMessage():String {
        return when(this) {
            RESULT_OK -> ""
            RESULT_ERROR_FIELD_IS_EMPTY -> "Field is empty"
            RESULT_ERROR_INCORRECT_FIELD_VALUE -> "Incorrect field value"
            RESULT_ERROR_EMPTY_RESULT -> "Result is empty"
            RESULT_ERROR_OBJECT_NOT_FOUND -> "Object not found"
            RESULT_ERROR_FIELD_ALREADY_EXISTS -> "Field with this value already exists"
        }
    }
}

