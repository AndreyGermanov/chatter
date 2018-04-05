/**
 * Created by andrey on 4/5/18.
 */
package utils

import java.text.SimpleDateFormat
import java.util.Date

/**
 * Structure defines log levels
 */
enum class LogLevel(level:String) {
    DEBUG("DEBUG"),
    WARNING("WARNING"),
    ERROR("ERROR"),
    INFO("INFO"),
}

/**
 * Global object, used to write logs to different targets: console. file. temporary browser
 * storage
 */
object Logger {

    // Array of enabled log levels. Messages with log level out of this list will
    // be silently ignored
    var loggerLevels = arrayOf(LogLevel.ERROR, LogLevel.WARNING, LogLevel.INFO, LogLevel.DEBUG)

    /**
     * Function used to format date to string
     * @param date: Input date
     * @return String representation of date
     */
    fun formatDate(date:Date):String {
        val format = SimpleDateFormat("YYYY-MM-dd HH:mm:ss")
        return format.format(date)
    }

    /**
     * Function used to log message
     *
     * @param logLevel: Log level
     * @param message: Log message
     * @param className: Name of class, which called this function to log message
     * @param methodName: Name of method of class, which called this function to log mesasge
     */
    fun log(logLevel:LogLevel = LogLevel.INFO, message:String, className:String="",methodName:String="") {
        if (this.loggerLevels.contains(logLevel)) {
            var date = Date()
            println(this.formatDate(date)+" - "+logLevel.name+": "+message+" ("+className+","+methodName+")")
        }
    }
}