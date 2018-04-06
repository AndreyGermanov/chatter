/**
 * Created by andrey on 2/9/18.
 */
package core
import interactors.Rooms
import interactors.Sessions
import interactors.Users
import io.javalin.Javalin
import io.javalin.embeddedserver.Location
import utils.LogLevel
import utils.Logger
import utils.SendMail

/**
 * Main class representing chat application. Implemented as singleton. It used to load all database collections,
 * start MongoDB, HTTP and WebSocket server. Also it starts SMTP agent, used to send emails and contains variables for
 * common system options
 *
 * @property dBServer Link to MongoDB Database server instance
 * @property webServer Link to Jetty HTTP server instance
 * @property msgServer Link to MessageCenter WebSocket class
 * @property smtpClient Link to SMTP Client instance
 *
 * @property users Iterable collection of chat user models
 * @property rooms Iterable collection of chat room models
 * @property sessions Iterable collection of chat user sessions models
 *
 * @property rootPath Base path of application
 * @property usersPath Root path of all users subfolders. There, user profile data can be stored
 * @property host Hostname or IP address of web server (and WebSocket server)
 * @property port Port of Web-server (and WebSocket server)
 * @property static_files_path Path with index.html file and other static assets which server displays as is
 *
 */
object ChatApplication {

    var webServer : Javalin = Javalin.create()

    lateinit var dBServer: DB

    var msgServer = MessageCenter

    lateinit var users: Users

    lateinit var sessions: Sessions

    lateinit var rooms: Rooms

    var rootPath = "opt/chatter"

    var usersPath = rootPath + "/users"

    var host = "http://192.168.0.214"

    var port = 8080

    var static_files_path = "opt/chatter/public"

    var smtpClient = SendMail()

    /**
     * Application starter. Function starts Web server, WebSocket message center, Database server and lads
     * main collections from database
     */
    fun run() {
        Logger.log(LogLevel.DEBUG,"Starting application","ChatApplication","run")
        Logger.log(LogLevel.DEBUG,"Setting database server","ChatApplication","run")
        this.dBServer = DB()
        Logger.log(LogLevel.DEBUG,"Loading main database collections","ChatApplication","run")
        this.users = Users(this.dBServer.db,"users")
        this.rooms = Rooms(this.dBServer.db,"rooms")
        this.sessions = Sessions(this.dBServer.db,"sessions")
        this.rooms.loadList(null){}
        this.users.loadList(null){}
        this.sessions.loadList(null){}
        Logger.log(LogLevel.DEBUG,"Initializing message center","ChatApplication","run")
        this.msgServer.setup()
        Logger.log(LogLevel.DEBUG,"Configuring Web Server...","ChatApplication","run")
        Logger.log(LogLevel.DEBUG,"Setting port of Web Server","ChatApplication","run")
        this.webServer.port(port)
        Logger.log(LogLevel.DEBUG,"Setting root path of static files o Web Server","ChatApplication","run")
        this.webServer.enableStaticFiles(static_files_path,Location.EXTERNAL)
        Logger.log(LogLevel.DEBUG,"Starting Web Server","ChatApplication","run")
        this.webServer.start()
    }
}