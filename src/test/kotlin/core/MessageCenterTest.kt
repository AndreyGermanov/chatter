package core

import interactors.Rooms
import interactors.Sessions
import interactors.Users
import io.javalin.Javalin
import io.javalin.embeddedserver.Location
import models.Room
import models.User
import org.bson.Document
import org.bson.types.ObjectId
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import java.io.FileInputStream
import java.net.URI
import java.nio.ByteBuffer
import java.util.zip.CRC32

/**
 * Created by andrey on 2/25/18.
 */
class MessageCenterTest {

    val app = ChatApplication
    var webSocketResponse:String = ""
    val parser = JSONParser()
    val client = WebSocketClient()
    val ws = SimpleEchoSocket()
    lateinit var session: Session

    @Before
    fun setUp() {
        app.dBServer = DB("test")
        app.dBServer.db.getCollection("rooms").deleteMany(Document())
        app.dBServer.db.getCollection("users").deleteMany(Document())
        app.dBServer.db.getCollection("sessions").deleteMany(Document())
        app.rooms = Rooms(app.dBServer.db,"rooms")
        app.users = Users(app.dBServer.db,"users")
        app.sessions = Sessions(app.dBServer.db,"sessions")
        app.webServer = Javalin.create()
        app.msgServer = MessageCenter
        app.host = "http://localhost"
        app.port = 8081
        app.webServer.port(app.port)
        app.msgServer.setup()
        app.webServer.enableStaticFiles(ChatApplication.static_files_path, Location.EXTERNAL)
        app.webServer.start()
        ws.delegate = this
        client.start()
        var con = client.connect(ws, URI("ws://localhost:"+app.port+"/websocket"))
        session = con.get()
    }

    @Test
    fun processFileRequestsQueue() {
        val request = JSONObject(mapOf("request_id" to "req1","request_timestamp" to (System.currentTimeMillis()/1000).toInt()))
        val req = mapOf(
                "session" to null,
                "request" to request) as HashMap<*, *>
        app.msgServer.file_requests.set(123456789,req)
        app.msgServer.file_requests.set(123456788,req)
        app.msgServer.processFileRequestsQueue()
        assertEquals("In the beginning should be"+app.msgServer.file_requests.count().toString()+" items",2,app.msgServer.file_requests.count())
        Thread.sleep((app.msgServer.PENDING_REQUEST_TIMEOUT*1000+1000).toLong())
        app.msgServer.processFileRequestsQueue()
        assertEquals("Queue should be empty after cleanup",0,app.msgServer.file_requests.count())
    }

    @Test
    fun activateUser() {
        var response = khttp.get(app.host+":"+app.port+"/activate/43234")
        assertEquals("Should fail if token is incorrect",406,response.statusCode)
        val user = User(app.dBServer.db,"users")
        user["_id"] = ObjectId.get()
        user["login"] = "bob"
        user["email"] = "test@test.com"
        user["password"] = "something"
        user.save{}
        app.users.addModel(user)
        response = khttp.get(app.host+":"+app.port+"/activate/"+user["_id"].toString())
        assertEquals("Should activate user account for correct user",200,response.statusCode)
        response = khttp.get(app.host+":"+app.port+"/activate/"+user["_id"].toString())
        assertEquals("Should not activate the same user twice",409,response.statusCode)
    }

    @Test
    fun registerUser() {
        session.remote.sendString("Nothing")
        Thread.sleep(200)
        var response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should return system error if not correct message format sent",
                MessageCenter.MessageObjectResponseCodes.INTERNAL_ERROR.toString(),
                response.get("status_code").toString())
        this.webSocketResponse = ""
        session.remote.sendString("{\"request_id\":\"12345\",\"action\":\"register_user\"}")
        Thread.sleep(200)
        response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should fail if no user login provided",
                Users.UserRegisterResultCode.RESULT_ERROR_NO_LOGIN.toString(),response.get("status_code").toString())
        this.webSocketResponse = ""
        session.remote.sendString("{\"request_id\":\"12345\",\"action\":\"register_user\",\"login\":\"\"}")
        Thread.sleep(200)
        response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should fail if empty user login provided",
                Users.UserRegisterResultCode.RESULT_ERROR_NO_LOGIN.toString(),response.get("status_code").toString())
        this.webSocketResponse = ""
        session.remote.sendString("{\"request_id\":\"12345\",\"action\":\"register_user\",\"login\":\"andrey\"}")
        Thread.sleep(200)
        response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should fail if no email provided",
                Users.UserRegisterResultCode.RESULT_ERROR_NO_EMAIL.toString(),response.get("status_code").toString())
        this.webSocketResponse = ""
        session.remote.sendString("{\"request_id\":\"12345\",\"action\":\"register_user\",\"login\":\"andrey\",\"email\":\"\"}")
        Thread.sleep(200)
        response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should fail if empty email provided",
                Users.UserRegisterResultCode.RESULT_ERROR_NO_EMAIL.toString(),response.get("status_code").toString())
        this.webSocketResponse = ""
        session.remote.sendString("{\"request_id\":\"12345\",\"action\":\"register_user\",\"login\":\"andrey\",\"email\":\"andrey@it-port.ru\"}")
        Thread.sleep(200)
        response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should fail no password provided",
                Users.UserRegisterResultCode.RESULT_ERROR_NO_PASSWORD.toString(),response.get("status_code").toString())
        this.webSocketResponse = ""
        session.remote.sendString("{\"request_id\":\"12345\",\"action\":\"register_user\",\"login\":\"andrey\",\"email\":\"andrey@it-port.ru\",\"password\":\"\"}")
        Thread.sleep(200)
        response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should fail if empty password provided",
                Users.UserRegisterResultCode.RESULT_ERROR_NO_PASSWORD.toString(),response.get("status_code").toString())
        this.webSocketResponse = ""
        session.remote.sendString("{\"request_id\":\"12345\",\"action\":\"register_user\",\"login\":\"andrey\",\"email\":\"andrey@it-port.ru\",\"password\":\"pass\"}")
        Thread.sleep(5000)
        response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should success if login,password and email provided",
                Users.UserRegisterResultCode.RESULT_OK.toString(),response.get("status_code").toString())
        val user = app.users.getBy("login","andrey")
        assertNotNull("Registered user should be added to collection",user)
        assertEquals("Registered user should be added to database",1,
        app.dBServer.db.getCollection("users").find(Document("login","andrey")).count())
    }

    @Test
    fun loginUser() {
        val request = JSONObject()
        request.set("request_id","12345")
        request.set("login","andrey")
        request.set("email","andrey@it-port.ru")
        request.set("password","pass")
        app.users.register(request) { result_code,user ->
            user!!["default_room"] = "r3"
            user["first_name"] = "Bob"
            user["last_name"] = "Johnson"
            user["gender"] = "M"
            user["birthDate"] = 12345678
            user.save{}

            val room1 = Room(app.dBServer.db,"rooms")
            room1["_id"] = "r1"
            room1["name"] = "Room 1"
            room1.save{}
            val room2 = Room(app.dBServer.db,"rooms")
            room2["_id"] = "r2"
            room2["name"] = "Room 2"
            room2.save{}
            app.rooms.addModel(room1)
            app.rooms.addModel(room2)
            session.remote.sendString("{\"request_id\":\"12345\",\"action\":\"login_user\"}")
            Thread.sleep(200)
            var response = parser.parse(this.webSocketResponse) as JSONObject
            assertEquals("Should fail if login not provided",
                    Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN.toString(),
                    response.get("status_code"))
            this.webSocketResponse = ""
            session.remote.sendString("{\"request_id\":\"12345\",\"action\":\"login_user\",\"login\":\"\"}")
            Thread.sleep(200)
            response = parser.parse(this.webSocketResponse) as JSONObject
            assertEquals("Should fail if login is empty",
                    Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN.toString(),
                    response.get("status_code"))
            this.webSocketResponse = ""
            session.remote.sendString("""{"request_id":"12345","action":"login_user","login":"nologin"}""")
            Thread.sleep(200)
            response = parser.parse(this.webSocketResponse) as JSONObject
            assertEquals("Should fail if login is incorrect",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD.toString(),
                    response.get("status_code").toString())
            this.webSocketResponse = ""
            session.remote.sendString("""{"request_id":"12345","action":"login_user","login":"andrey"}""")
            Thread.sleep(200)
            response = parser.parse(this.webSocketResponse) as JSONObject
            assertEquals("Should fail if no password provided",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD.toString(),
                    response.get("status_code").toString())
            this.webSocketResponse = ""
            session.remote.sendString("""{"request_id":"12345","action":"login_user","login":"andrey","password":""}""")
            Thread.sleep(200)
            response = parser.parse(this.webSocketResponse) as JSONObject
            assertEquals("Should fail if empty password provided",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD.toString(),
                    response.get("status_code").toString())
            this.webSocketResponse = ""
            session.remote.sendString("""{"request_id":"12345","action":"login_user","login":"andrey","password":"no"}""")
            Thread.sleep(2000)
            response = parser.parse(this.webSocketResponse) as JSONObject
            assertEquals("Should fail if incorrect password provided",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD.toString(),
                    response.get("status_code").toString())
            this.webSocketResponse = ""
            session.remote.sendString("""{"request_id":"12345","action":"login_user","login":"andrey","password":"pass"}""")
            Thread.sleep(2000)
            response = parser.parse(this.webSocketResponse) as JSONObject
            assertEquals("Should fail if user account is not activated",Users.UserLoginResultCode.RESULT_ERROR_NOT_ACTIVATED.toString(),
                    response.get("status_code").toString())
            user["active"] = true
            user.save{}
            this.webSocketResponse = ""
            session.remote.sendString("""{"request_id":"12345","action":"login_user","login":"andrey","password":"pass"}""")
            Thread.sleep(2000)
            response = parser.parse(this.webSocketResponse) as JSONObject
            assertEquals("Should success if user account activated and login and password correct",
                    Users.UserLoginResultCode.RESULT_OK.toString(),
                    response.get("status_code").toString())
            val success_response = response
            this.webSocketResponse = ""
            session.remote.sendString("""{"request_id":"12345","action":"login_user","login":"andrey","password":"pass"}""")
            Thread.sleep(2000)
            response = parser.parse(this.webSocketResponse) as JSONObject
            assertEquals("Should fail to login again, of already login",
                    Users.UserLoginResultCode.RESULT_ERROR_ALREADY_LOGIN.toString(),
                    response.get("status_code").toString())
            Thread.sleep((app.users.USER_ACTIVITY_TIMEOUT*1000+1000).toLong())
            this.webSocketResponse = ""
            session.remote.sendString("""{"request_id":"12345","action":"login_user","login":"andrey","password":"pass"}""")
            Thread.sleep(2000)
            response = parser.parse(this.webSocketResponse) as JSONObject
            assertEquals("Should relogin, if user lost activity",
                    Users.UserLoginResultCode.RESULT_OK.toString(),
                    response.get("status_code").toString())
            assertEquals("Should be single session for this user in memory",1,app.sessions.getListBy("user_id",user["_id"].toString())!!.count())
            assertEquals("Should be single session for this user in database",1,
                    app.dBServer.db.getCollection("sessions").find(Document("user_id",user["_id"].toString())).count())
            val sess = app.sessions.getBy("user_id",user["_id"].toString()) as models.Session
            assertEquals("Should return correct user_id",response.get("user_id").toString(),user["_id"].toString())
            assertEquals("Should return correct session_id",response.get("session_id").toString(),sess["_id"].toString())
            assertEquals("Should return correct login",response.get("login").toString(),user["login"].toString())
            assertEquals("Should return correct email",response.get("email").toString(),user["email"].toString())
            assertEquals("Should return correct first_name",response.get("first_name").toString(),user["first_name"].toString())
            assertEquals("Should return correct last_name",response.get("last_name").toString(),user["last_name"].toString())
            assertEquals("Should return correct gender",response.get("gender").toString(),user["gender"].toString())
            assertEquals("Should return correct birthDate",response.get("birthDate").toString(),user["birthDate"].toString())

            val rooms = parser.parse(response.get("rooms").toString()) as JSONArray
            assertEquals("Should return correct number of rooms",app.rooms.count(),rooms.count())
            val room = rooms.get(0) as JSONObject
            assertTrue("Room must have correct structure","Room 1" == room.get("name").toString() && "r1" == room.get("_id").toString())
            assertNull("Should not return default_room if it does not exist",response.get("default_room"))
            user["default_room"] = "r2"
            user.save{}
            Thread.sleep((app.users.USER_ACTIVITY_TIMEOUT*1000+1000).toLong())
            this.webSocketResponse = ""
            session.remote.sendString("""{"request_id":"12345","action":"login_user","login":"andrey","password":"pass"}""")
            Thread.sleep(2000)
            response = parser.parse(this.webSocketResponse) as JSONObject
            assertNotNull("Should return default_room if it does exist",response.get("default_room"))
        }
    }

    @Test
    fun updateUser() {
        val room1 = Room(app.dBServer.db,"rooms")
        room1["_id"] = "r1"
        room1["name"] = "Room 1"
        room1.save{}
        val room2 = Room(app.dBServer.db,"rooms")
        room2["_id"] = "r2"
        room2["name"] = "Room 2"
        room2.save{}
        app.rooms.addModel(room1)
        app.rooms.addModel(room2)
        this.webSocketResponse = ""
        session.remote.sendString("""{"request_id":"12345","action":"update_user"}""")
        Thread.sleep(200)
        var response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should fail if user_id is not provided",MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR.toString(),
                response.get("status_code").toString())
        this.webSocketResponse = ""
        session.remote.sendString("""{"request_id":"12345","action":"update_user","user_id":""}""")
        Thread.sleep(200)
        response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should fail if empty user_id provided",MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR.toString(),
                response.get("status_code").toString())
        this.webSocketResponse = ""
        session.remote.sendString("""{"request_id":"12345","action":"update_user","user_id":"sdsdfs"}""")
        Thread.sleep(200)
        response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should fail if incorrect user_id provided",MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR.toString(),
                response.get("status_code").toString())
        val request = JSONObject()
        request.set("request_id","12345")
        request.set("login","andrey")
        request.set("email","andrey@it-port.ru")
        request.set("password","pass")
        app.users.register(request) { result_code, user ->
            user!!["active"] = true
            user.save{}
            session.remote.sendString("""{"request_id":"12345","action":"update_user","user_id":"""" + user!!["_id"] + """"}""")
            Thread.sleep(200)
            response = parser.parse(this.webSocketResponse) as JSONObject
            assertEquals("Should fail if session_id is not provided",
                    MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR.toString(),
                    response.get("status_code").toString())
            this.webSocketResponse = ""
            session.remote.sendString("""{"request_id":"12345","action":"update_user","user_id":"""" + user!!["_id"] + """","session_id":"123"}""")
            Thread.sleep(200)
            response = parser.parse(this.webSocketResponse) as JSONObject
            assertEquals("Should fail if incorrect session_id provided",
                    MessageCenter.MessageObjectResponseCodes.AUTHENTICATION_ERROR.toString(),
                    response.get("status_code").toString())
            app.users.login("andrey","pass") { result_code,result ->
                val sess = app.sessions.getBy("user_id",user!!["_id"].toString()) as models.Session
                this.webSocketResponse = ""
                session.remote.sendString("""{"request_id":"12345","action":"update_user","user_id":"""" +
                        user!!["_id"] + """","session_id":""""+sess.get("_id").toString()+""""}""")
                Thread.sleep(200)
                response = parser.parse(this.webSocketResponse) as JSONObject
                assertEquals("Should pass if correct session_id provided",
                        Users.UserUpdateResultCode.RESULT_OK.toString(),
                        response.get("status_code").toString())
                val stream = FileInputStream("src/test/resources/profile.png")
                val img = stream.readBytes()
                val checksumEngine = CRC32()
                checksumEngine.update(img)
                val img_checksum = checksumEngine.value
                this.webSocketResponse = ""
                session.remote.sendString("""{"request_id":"12345","action":"update_user","user_id":"""" +
                        user!!["_id"] + """","session_id":""""+sess.get("_id").toString()+"""","profile_image_checksum":"""+img_checksum.toString()+"""}""")
                session.remote.sendBytes(ByteBuffer.wrap(img))
                Thread.sleep(5000)
                response = parser.parse(this.webSocketResponse) as JSONObject
                assertEquals("Should pass if profile image upload correctly",
                        Users.UserUpdateResultCode.RESULT_OK.toString(),
                        response.get("status_code").toString())
                assertEquals("Image upload response should be bound to correct request_id",
                        "12345",
                        response.get("request_id").toString())
            }

        }

    }

}