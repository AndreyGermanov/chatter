/**
 * Created by andrey on 2/24/18.
 */
package utils

import org.json.simple.JSONObject


/**
 * Function used to stringify some enum values in source JSON object and return JSON string with values either
 *
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
