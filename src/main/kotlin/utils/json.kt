/**
 * Created by andrey on 2/24/18.
 */
package utils

import controllers.AdminControllerRequestResults
import org.eclipse.jetty.websocket.api.Session
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser


/**
 * Function used to stringify some enum values in source JSON object and return JSON string with values either
 * @param response: object to convert to string
 * @returns JSON string
 */
fun toJSONString(response: JSONObject):String {
    for ((index,i) in response) {
        if (i !is String && i !is Int && i !is Long && i !is Boolean) {
            response.set(index,i.toString())
        }
    }
    val result = response.toJSONString()
    return result
}

/**
 * Function tries to parse [field_object] attribute as JSONArray and returns it inside
 * response object in "result" field case of success.
 *
 * @param field_object - Object, which contains field JSON data,either as string, or already as
 * JSONArray
 * @return JSONObject with result of operation. Consists of 'status'=(ok or error),
 * 'status_code' with one of values of JSONParseResult enumeration
 */
fun parseJSONArray(field_object:Any?):JSONObject {
    var field: JSONArray? = null
    val parser = JSONParser()
    val response = JSONObject()
    var fieldsParseError = false
    if (field_object is String) {
        try {
            field = parser.parse(field_object.toString()) as JSONArray
        } catch (e:Exception) {
            Logger.log(LogLevel.WARNING,"Could not parse field to JSON: $field_object ",
                    "top","parseJSONArray")
            fieldsParseError = true
        }
    } else if (field_object is JSONArray) {
        try {
            field = field_object
        } catch (e:Exception) {
            Logger.log(utils.LogLevel.WARNING,"Could not parse fields to JSON: $field_object",
                    "top","parseJSONArray")
            fieldsParseError = true
        }
    }
    if (fieldsParseError) {
        response["status"] = "error"
        response["status_code"] = JSONParseResult.RESULT_ERROR_INCORRECT_FIELD_VALUE
        response["field"] = "fields"
        return response
    }
    if (field == null || field.count() == 0) {
        response["status"] = "error"
        response["status_code"] = JSONParseResult.RESULT_ERROR_FIELD_IS_EMPTY
        response["field"] = "fields"
        Logger.log(LogLevel.WARNING,"Fields is empty: $field",
                "AdminController","admin_add_user.exec")
        return response
    }
    response["status"] = "ok"
    response["status_code"] = JSONParseResult.RESULT_OK
    response["result"] = field
    return response
}


/**
 * Function extracts field with 'fieldName' from response and tried to parse it
 * as JSON array. If it successful, it returns this JSONArray inside 'result' field
 * of response. Otherwise it returns response with 'status'='error' and 'status_code'
 * of error
 *
 * @param request: Request to parse
 * @param fieldName: Name of field in request to parse as JSONArray
 * @param session: Link to WebSocket Client session instance
 * @returns response object which contains results of operation. If error, then response
 * consist of 'status'='error', status_code = 'RESULT_ERROR_INCORRECT_FIELD_VALUE' or
 * 'RESULT_ERROR_FIELD_IS_EMPTY'
 */
fun parseJSONArrayFromRequest(request:JSONObject,fieldName:String,session: Session?=null):JSONObject {
    val logInfo = " Request: $request"
    Logger.log(LogLevel.DEBUG,"Beginning to parse field '$fieldName' as JSONArray. $logInfo",
            "top","parseJSONArrayFromRequest")
    val response = parseJSONArray(request[fieldName])
    return response
}

/**
 * Function transfers field_object to JSONObject and returns result of this operation
 *
 * @param field_object: Object of any type, which should be converted to JSONObject
 * @return JSONObject if possible to convert or null if it not possible
 */
fun toJSONObject(field_object:Any?):JSONObject? {
    var field: JSONObject? = null
    val parser = JSONParser()
    if (field_object is String) {
        try {
            field = parser.parse(field_object.toString()) as JSONObject
        } catch (e:Exception) {
            Logger.log(LogLevel.WARNING,"Could not parse field $field_object to JSON.",
                    "top","toJSONObject")
        }
    } else if (field_object !is JSONObject) {
        Logger.log(LogLevel.WARNING,"Unknown field definition format for $field_object.",
                "top","toJSONObject")
    } else {
        field = field_object
    }
    return field
}

enum class JSONParseResult(val value:String) {
    RESULT_OK("RESULT_OK"),
    RESULT_ERROR_FIELD_IS_EMPTY("RESULT_ERROR_FIELD_IS_EMPTY"),
    RESULT_ERROR_INCORRECT_FIELD_VALUE("RESULT_ERROR_INCORRECT_FIELD_VALUE")
}
