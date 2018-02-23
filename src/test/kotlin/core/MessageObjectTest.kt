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
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import utils.BCrypt
import utils.SendMail
import java.io.FileInputStream
import java.util.zip.Adler32

/**
 * Created by andrey on 2/23/18.
 */
class MessageObjectTest {

    val app = ChatApplication
    lateinit var wsHandler: MessageObject
    lateinit var db: MongoDatabase

    @Before
    fun setUp() {
        app.dBServer = DB("test")
        app.webServer = Javalin.create()
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
        val checksumEngine = Adler32()
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
        assertNotNull("Should return list of rooms as JSON array",roomsObj)
        assertTrue("List of rooms should be JSON array",roomsObj is JSONArray)
        var rooms = roomsObj as JSONArray
        assertEquals("Should return correct number of room ids",3,rooms.count())
        val room = rooms.get(0) as JSONObject
        assertEquals("Should return correct room ids","r1",room.get("_id").toString())
    }

    @Test
    fun updateUser() {

    }

    @Test
    fun onWebSocketError() {

    }

    @Test
    fun onWebSocketText() {

    }

    @Test
    fun onWebSocketConnect() {

    }

    @Test
    fun onWebSocketClose() {

    }

    @Test
    fun onWebSocketBinary() {

    }

}