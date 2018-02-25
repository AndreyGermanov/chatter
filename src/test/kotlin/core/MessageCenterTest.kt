package core

import interactors.Rooms
import interactors.Sessions
import interactors.Users
import io.javalin.Javalin
import io.javalin.embeddedserver.Location
import models.User
import org.bson.Document
import org.bson.types.ObjectId
import org.json.simple.JSONObject
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before

/**
 * Created by andrey on 2/25/18.
 */
class MessageCenterTest {

    val app = ChatApplication

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
        app.msgServer = MessageCenter()

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
        app.host = "http://localhost"
        app.port = 8081
        app.webServer.port(app.port)
        app.webServer.enableStaticFiles(ChatApplication.static_files_path, Location.EXTERNAL)
        app.webServer.start()
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

}