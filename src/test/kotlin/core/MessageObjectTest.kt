package core

import com.mongodb.client.MongoDatabase
import com.mongodb.util.JSON
import interactors.Rooms
import interactors.Sessions
import interactors.Users
import io.javalin.Javalin
import models.Room
import models.Session
import models.User
import org.bson.Document
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import utils.BCrypt
import utils.toJSONString
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.CRC32


/**
 * Created by andrey on 2/23/18.
 */

class MessageObjectTest {

    val app = ChatApplication
    lateinit var wsHandler: MessageObject
    lateinit var db: MongoDatabase
    val parser = JSONParser()

    @Before
    fun setUp() {
        app.dBServer = DB("test")
        app.webServer = Javalin.create()
        app.port = 8082
        app.msgServer = MessageCenter()
        wsHandler = app.msgServer.wsHandler as MessageObject
        db = app.dBServer.db
        db.getCollection("users").deleteMany(Document())
        db.getCollection("sessions").deleteMany(Document())
        db.getCollection("rooms").deleteMany(Document())
        app.users = Users(db,"users")
        app.rooms = Rooms(db,"rooms")
        app.sessions = Sessions(db,"sessions")
        val room1 = Room(db,"rooms")
        room1["_id"] = "r1"
        room1["name"] = "Room 1"
        room1.save{}
        val room2 = Room(db,"rooms")
        room2["_id"] = "r2"
        room2["name"] = "Room 2"
        room2.save{}
        val room3 = Room(db,"rooms")
        room3["_id"] = "r3"
        room3["name"] = "Room 3"
        room3.save{}
        app.rooms.loadList(null){}
    }

    @Test
    fun registerUser() {
        val params = JSONObject()
        var result = wsHandler.registerUser(params)
        assertTrue("Should return result as JSON object",result is JSONObject)
        assertNotEquals("Should return status of operation",null,result.get("status"))
        assertNotEquals("Should return status code of operation",null,result.get("status_code"))
        assertEquals("Should return error if login not specified",Users.UserRegisterResultCode.RESULT_ERROR_NO_LOGIN.toString(),result.get("status_code").toString())
        params.set("login","")
        result = wsHandler.registerUser(params)
        assertEquals("Should return error if login is empty",Users.UserRegisterResultCode.RESULT_ERROR_NO_LOGIN.toString(),result.get("status_code").toString())
        params.set("login","andrey")
        result = wsHandler.registerUser(params)
        assertEquals("Should return error if email not specified",Users.UserRegisterResultCode.RESULT_ERROR_NO_EMAIL.toString(),result.get("status_code").toString())
        params.set("email","")
        result = wsHandler.registerUser(params)
        assertEquals("Should return error if email is empty",Users.UserRegisterResultCode.RESULT_ERROR_NO_EMAIL.toString(),result.get("status_code").toString())
        params.set("email","andrey@it-port.ru")
        result = wsHandler.registerUser(params)
        assertEquals("Should return error if password is not specified",Users.UserRegisterResultCode.RESULT_ERROR_NO_PASSWORD.toString(),result.get("status_code").toString())
        params.set("password","")
        result = wsHandler.registerUser(params)
        assertEquals("Should return error if password is not specified",Users.UserRegisterResultCode.RESULT_ERROR_NO_PASSWORD.toString(),result.get("status_code").toString())
        params.set("password","pass")
        result = wsHandler.registerUser(params)
        assertEquals("Should return ok if all settings correct",Users.UserRegisterResultCode.RESULT_OK.toString(),result.get("status_code").toString())
        app.users.loadList(null) {}
        val user = app.users.getBy("login","andrey") as User
        assertNotEquals("Should add user to database",null,user)
        assertEquals("Should have correct login","andrey",user["login"].toString())
        assertTrue("Should have correct passowrd", BCrypt.checkpw("pass",user["password"].toString()))
        assertEquals("Should have correct email","andrey@it-port.ru",user["email"].toString())
    }

    @Test
    fun loginUser() {
        registerUser()
        val user = app.users.getBy("login","andrey") as User
        user["first_name"] = "Bob"
        user["last_name"] = "Johnson"
        user["gender"] = "M"
        user["default_room"] = "r1"
        user["birthDate"] = 1234567890
        user.save(){}
        val stream = FileInputStream("src/test/resources/profile.png")
        val img = stream.readBytes()
        val checksumEngine = CRC32()
        checksumEngine.update(img)
        val img_checksum = checksumEngine.value
        user.setProfileImage(img){}

        val params = JSONObject()
        var result = wsHandler.loginUser(params)
        assertTrue("Result should be a JSON object",result is JSONObject)
        assertNotEquals("Result should contain status field",null,result.get("status"))
        assertEquals("Should return error without login",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN.toString(),result.get("status_code").toString())
        params.set("login","")
        result = wsHandler.loginUser(params)
        assertEquals("Should return error without login",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN.toString(),result.get("status_code").toString())
        params.set("login",null)
        params.set("password","test")
        result = wsHandler.loginUser(params)
        assertEquals("Should return error with incorrect login",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN.toString(),result.get("status_code").toString())
        params.set("login","weird")
        result = wsHandler.loginUser(params)
        assertEquals("Should return error with incorrect login",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN.toString(),result.get("status_code").toString())
        params.set("login","andrey")
        params.remove("password")
        result = wsHandler.loginUser(params)
        assertEquals("Should return error without password",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD.toString(),result.get("status_code").toString())
        params.set("password","")
        result = wsHandler.loginUser(params)
        assertEquals("Should return error without password",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD.toString(),result.get("status_code").toString())
        params.set("password","NOP")
        result = wsHandler.loginUser(params)
        assertEquals("Should return error with incorrect password",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD.toString(),result.get("status_code").toString())
        params.set("password","pass")
        result = wsHandler.loginUser(params)
        assertEquals("Should return error if user account is not activated",Users.UserLoginResultCode.RESULT_ERROR_NOT_ACTIVATED.toString(),result.get("status_code").toString())
        user["active"] = true
        user.save{}
        result = wsHandler.loginUser(params)
        assertEquals("Should return success if account is activated and login and password are correct",Users.UserLoginResultCode.RESULT_OK.toString(),result.get("status_code").toString())
        result = wsHandler.loginUser(params)
        assertEquals("Should return error, if user attempts to login again before inactivity timeout exist",Users.UserLoginResultCode.RESULT_ERROR_ALREADY_LOGIN.toString(),result.get("status_code").toString())
        Thread.sleep(15000)
        result = wsHandler.loginUser(params)
        assertEquals("Should relogin user, if he logins again after inactivity timeout",Users.UserLoginResultCode.RESULT_OK.toString(),result.get("status_code").toString())
        assertEquals("Only single session should be created for this user in database",1, db.getCollection("sessions").find(Document()).count())
        assertEquals("Only single session should be loaded to memory",1, app.sessions.count())
        assertNotNull("Should return session_id in response",result.get("session_id"))
        val sessionObj = app.sessions.getById(result.get("session_id").toString())
        assertNotNull("Should return correct session_id",sessionObj)
        val session = sessionObj as Session
        assertEquals("Should return correct user_id",user["_id"].toString(),result.get("user_id").toString())
        assertEquals("Should return correct login",user["login"].toString(),result.get("login").toString())
        assertEquals("Should return correct first_name",user["first_name"].toString(),result.get("first_name").toString())
        assertEquals("Should return correct last_name",user["last_name"].toString(),result.get("last_name").toString())
        assertEquals("Should return correct gender",user["gender"].toString(),result.get("gender").toString())
        assertEquals("Should return correct birthDate",user["birthDate"].toString().toInt(),result.get("birthDate").toString().toInt())
        assertEquals("Should return correct default_room",user["default_room"].toString(),result.get("default_room"))
        assertEquals("Should return correct profile image",img_checksum,result.get("checksum").toString().toLong())
        assertEquals("Should set correct login time in session",(System.currentTimeMillis()/1000).toInt(),session["loginTime"].toString().toInt())
        assertEquals("Should set correct last activity time in session",(System.currentTimeMillis()/1000).toInt(),session["lastActivityTime"].toString().toInt())
        val roomsObj = result.get("rooms")
        assertNotNull("Should return list of rooms",roomsObj)
        assertTrue("List of rooms should be JSON array",roomsObj is JSONArray)
        var rooms = roomsObj as JSONArray
        assertEquals("Should return correct number of room ids",3,rooms.count())
        val room = rooms.get(0) as JSONObject
        assertEquals("Should return correct room ids","r1",room.get("_id").toString())
    }

    @Test
    fun updateUser() {
        registerUser()
        val user = app.users.getBy("login","andrey") as User
        val stream = FileInputStream("src/test/resources/profile.png")
        val img = stream.readBytes()
        val checksumEngine = CRC32()
        checksumEngine.update(img)
        val img_checksum = checksumEngine.value
        app.users.activate(user!!["_id"].toString()){}
        app.users.login(user!!["login"].toString(),user!!["password"].toString()) { p1,p2 ->
            var params = JSONObject()
            var result = wsHandler.updateUser(params)
            assertTrue("Should return result as JSON object",result is JSONObject)
            assertNotNull("Result should have status field",result.get("status"))
            assertNotNull("Result should have status_code field",result.get("status_code"))
            assertEquals("Should fail if user_id is not specified",Users.UserUpdateResultCode.RESULT_ERROR_USER_NOT_SPECIFIED.toString(),result.get("status_code").toString())
            params.set("user_id","ID1")
            result = wsHandler.updateUser(params)
            assertEquals("Should fail if user with specified id not found",Users.UserUpdateResultCode.RESULT_ERROR_USER_NOT_FOUND.toString(),result.get("status_code").toString())
            params.set("user_id",user["_id"].toString())
            params.set("first_name","")
            result = wsHandler.updateUser(params)
            assertTrue("Should not accept empty first_name",result.get("status_code").toString() == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY.toString() && result.get("field").toString() == "first_name")
            params.set("first_name","Bob")
            params.set("last_name","")
            result = wsHandler.updateUser(params)
            assertTrue("Should not accept empty last_name",result.get("status_code").toString() == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY.toString() && result.get("field").toString() == "last_name")
            params.set("last_name","Johnson")
            params.set("gender","")
            result = wsHandler.updateUser(params)
            assertTrue("Should not accept empty gender",result.get("status_code").toString() == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY.toString() && result.get("field").toString() == "gender")
            params.set("gender","Fake")
            result = wsHandler.updateUser(params)
            assertTrue("Should not accept incorrect gender",result.get("status_code").toString() == Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE.toString() && result.get("field").toString() == "gender")
            params.set("gender","M")
            params.set("birthDate","")
            result = wsHandler.updateUser(params)
            assertTrue("Should not accept empty birthDate",result.get("status_code").toString() == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY.toString() && result.get("field").toString() == "birthDate")
            params.set("birthDate",9999999999)
            result = wsHandler.updateUser(params)
            assertTrue("Should not accept incorrect birthDate",result.get("status_code").toString() == Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE.toString() && result.get("field").toString() == "birthDate")
            params.set("birthDate",1234567890)
            params.set("default_room","")
            result = wsHandler.updateUser(params)
            assertTrue("Should not accept empty default_room",result.get("status_code").toString() == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY.toString() && result.get("field").toString() == "default_room")
            params.set("default_room","NO_ROOM")
            result = wsHandler.updateUser(params)
            assertTrue("Should not accept incorrect default_room",result.get("status_code").toString() == Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE.toString() && result.get("field").toString() == "default_room")
            params.set("default_room","r1")
            result = wsHandler.updateUser(params)
            assertEquals("Should write successfully if all params ok ",Users.UserUpdateResultCode.RESULT_OK.toString(),result.get("status_code").toString())
            params.set("profile_image_checksum","")
            result = wsHandler.updateUser(params)
            assertTrue("Should not accept empty profile image checksum",result.get("status_code").toString() == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY.toString() && result.get("field").toString() == "profile_image_checksum")
            params.set("profile_image_checksum","dfwdfdfdf")
            result = wsHandler.updateUser(params)
            assertTrue("Should not accept incorrect profile image checksum",result.get("status_code").toString() == Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE.toString() && result.get("field").toString() == "profile_image_checksum")
            params.set("profile_image_checksum",img_checksum)
            result = wsHandler.updateUser(params)
            assertEquals("Should write successfully if all params ok ",Users.UserUpdateResultCode.RESULT_OK_PENDING_IMAGE_UPLOAD.toString(),result.get("status_code").toString())
            assertEquals("User first_name updated correctly","Bob",user["first_name"].toString())
            assertEquals("User last_name updated correctly","Johnson",user["last_name"].toString())
            assertEquals("User gender updated correctly","M",user["gender"].toString())
            assertEquals("User birthDate updated correctly",1234567890,user["birthDate"].toString().toInt())
            assertEquals("User default_room updated correctly","r1",user["default_room"].toString())
            val pending_request = app.msgServer.file_requests.get(img_checksum) as HashMap<String,Any?>
            assertNotNull("Should place image upload pending request",pending_request)
            assertNotNull("Should contain 'request' object ",pending_request.get("request"))
            assertNotNull("Should contain link to websocket session",pending_request.get("session"))
            app.msgServer.runCronjob()
            Thread.sleep((app.msgServer.PENDING_REQUEST_TIMEOUT*1000+5000).toLong())
            assertEquals("Should remove file request if it outdated",0,app.msgServer.file_requests.size)

        }
    }

    @Test
    fun onWebSocketText() {
        var response = JSONObject()
        wsHandler.onWebSocketText(null)
        try {
            response = parser.parse(wsHandler.lastResponse) as JSONObject
        } catch (e:Exception) {
            fail("Should return correct JSON")
        }
        assertEquals("Should fail if no message provided",MessageObject.MessageObjectResponseCodes.INTERNAL_ERROR.toString(),response.get("status_code"))
        wsHandler.onWebSocketText("}action:5{")
        response = parser.parse(wsHandler.lastResponse) as JSONObject
        assertEquals("Should fail if incorrect JSON message provided",MessageObject.MessageObjectResponseCodes.INTERNAL_ERROR.toString(),response.get("status_code"))
        wsHandler.onWebSocketText("8F?????'+-4ss??????/14467>>????<<@@flpt8?BDFF????????????????3:==?@????????'%'+068=???????????j?j0k6k?o?o????g?i?{???????????B?D??6?;?l?u?u??????????????????!?#?$?&?*?????D?J??@`'kg'i''??_?n?t?n?(?}???}???}???}???}???}?????o}??}??}???}??????????'?-?4?}?;?}?F?}?M?}?Y?}?`?}?l?}?s?}??}???}??")
        response = parser.parse(wsHandler.lastResponse) as JSONObject
        assertEquals("Should fail if garbage binary data provided",MessageObject.MessageObjectResponseCodes.INTERNAL_ERROR.toString(),response.get("status_code"))
        var request = JSONObject()
        request.set("request_id","")
        wsHandler.onWebSocketText(toJSONString(request))
        response = parser.parse(wsHandler.lastResponse) as JSONObject
        assertEquals("Should fail if request_id is empty",MessageObject.MessageObjectResponseCodes.INTERNAL_ERROR.toString(),response.get("status_code").toString())
        request.set("request_id","12345")
        wsHandler.onWebSocketText(toJSONString(request))
        response = parser.parse(wsHandler.lastResponse) as JSONObject
        assertEquals("Should fail if no user_id provided (except register_user and login_user_actions",MessageObject.MessageObjectResponseCodes.AUTHENTICATION_ERROR.toString(),response.get("status_code").toString())
        request.set("user_id","boom")
        wsHandler.onWebSocketText(toJSONString(request))
        response = parser.parse(wsHandler.lastResponse) as JSONObject
        assertEquals("Should fail if provided user_id is not correct",MessageObject.MessageObjectResponseCodes.AUTHENTICATION_ERROR.toString(),response.get("status_code").toString())
        val userObj = JSONObject()
        userObj.set("login","andrey")
        userObj.set("email","andrey@it-port.ru")
        userObj.set("password","pass")
        app.users.register(userObj) {result_code,user ->
            request.set("user_id",user!!["_id"].toString())
            wsHandler.onWebSocketText(toJSONString(request))
            response = parser.parse(wsHandler.lastResponse) as JSONObject
            assertEquals("Should fail if no session_id provided",MessageObject.MessageObjectResponseCodes.AUTHENTICATION_ERROR.toString(),response.get("status_code").toString())
            request.set("session_id","")
            wsHandler.onWebSocketText(toJSONString(request))
            response = parser.parse(wsHandler.lastResponse) as JSONObject
            assertEquals("Should fail if empty session_id provided",MessageObject.MessageObjectResponseCodes.AUTHENTICATION_ERROR.toString(),response.get("status_code").toString())
            request.set("session_id","12323")
            wsHandler.onWebSocketText(toJSONString(request))
            response = parser.parse(wsHandler.lastResponse) as JSONObject
            assertEquals("Should fail if incorrect session_id provided",MessageObject.MessageObjectResponseCodes.AUTHENTICATION_ERROR.toString(),response.get("status_code").toString())
            user["active"] = true
            user.save{}
            app.users.login("andrey","pass") { result_code,result ->
                val user_session = app.sessions.getBy("user_id",user["_id"].toString()) as models.Session
                request.set("session_id",user_session["_id"].toString())
                wsHandler.onWebSocketText(toJSONString(request))
                response = parser.parse(wsHandler.lastResponse) as JSONObject
                assertEquals("Should fail if no action provided",MessageObject.MessageObjectResponseCodes.INTERNAL_ERROR.toString(),response.get("status_code").toString())
                request.set("action","")
                wsHandler.onWebSocketText(toJSONString(request))
                response = parser.parse(wsHandler.lastResponse) as JSONObject
                assertEquals("Should fail if empty action provided",MessageObject.MessageObjectResponseCodes.INTERNAL_ERROR.toString(),response.get("status_code").toString())
                request.set("action","boom")
                wsHandler.onWebSocketText(toJSONString(request))
                response = parser.parse(wsHandler.lastResponse) as JSONObject
                assertEquals("Should fail if incorrect action provided",MessageObject.MessageObjectResponseCodes.INTERNAL_ERROR.toString(),response.get("status_code").toString())
                request.set("action","update_user")
                wsHandler.onWebSocketText(toJSONString(request))
                response = parser.parse(wsHandler.lastResponse) as JSONObject
                assertEquals("Should successfully process request if all params OK",Users.UserUpdateResultCode.RESULT_OK.toString(),response.get("status_code").toString())
                assertNotNull("Should successfully process request and return request_id",response.get("request_id"))
                assertEquals("Should return correct request id",request.get("request_id").toString(),response.get("request_id").toString())
            }
        }
    }

    @Test
    fun onWebSocketBinary() {
        wsHandler.lastResponse = ""
        val stream = FileInputStream("src/test/resources/profile.png")
        val img = stream.readBytes()
        val checksumEngine = CRC32()
        checksumEngine.update(img)
        val img_checksum = checksumEngine.value
        app.msgServer.runCronjob()
        wsHandler.onWebSocketBinary(null,0,0)
        assertEquals("Should not return anything on empty requests","",wsHandler.lastResponse)
        wsHandler.onWebSocketBinary(kotlin.ByteArray(10),0,0)
        assertEquals("Should not return anything on unknown byte arrays","",wsHandler.lastResponse)
        var params = JSONObject()
        params.set("login","andrey")
        params.set("password","pass")
        params.set("email","andrey@it-port.ru")
        app.users.register(params) { result_code,user ->
            user!!["active"] = true
            user!!.save{}
            params = JSONObject()
            params.set("login","andrey")
            params.set("password","pass")
            params.set("request_id",23456)
            var response = wsHandler.loginUser(params)
            var session_id = response.get("session_id").toString()
                params = JSONObject()
                params.set("first_name", "Andrey")
                params.set("profile_image_checksum",img_checksum)
                params.set("user_id",user!!["_id"].toString())
                params.set("session_id",session_id)
                params.set("request_id",1234)
                params.set("action","update_user")
                wsHandler.onWebSocketText(toJSONString(params))
                response = parser.parse(wsHandler.lastResponse) as JSONObject
                assertEquals("Should return pending upload image response",Users.UserUpdateResultCode.RESULT_OK_PENDING_IMAGE_UPLOAD.toString(),response.get("status_code").toString())
                wsHandler.onWebSocketBinary(img,0,0)
                response = parser.parse(wsHandler.lastResponse) as JSONObject
                assertNotNull("Should contain status field in response",response.get("status"))
                assertNotNull("Should contain request_id field in response",response.get("request_id"))
                assertEquals("Should contain correct request_id field in response",1234,response.get("request_id").toString().toInt())
                assertEquals("Should return success after image writing",Users.UserUpdateResultCode.RESULT_OK.toString(),response.get("status_code").toString())
                assertNull("Should not contain record in file_requests queue",app.msgServer.file_requests.get(img_checksum))
                assertTrue("Profile image file written to correct destination", Files.exists(Paths.get(app.usersPath+"/"+user["_id"]+"/profile.png")))
                val stream = FileInputStream(app.usersPath+"/"+user["_id"]+"/profile.png")
                val bytes = stream.readBytes()
                checksumEngine.reset()
                checksumEngine.update(bytes)
                assertEquals("Profile image file has correct content",img_checksum,checksumEngine.value)
        }
    }
}