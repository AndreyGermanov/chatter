package controllers

import core.*
import interactors.Rooms
import interactors.Sessions
import interactors.Users
import io.javalin.Javalin
import junit.framework.Assert.assertEquals
import models.Room
import models.Session
import models.User
import org.bson.Document
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.junit.Before
import org.junit.Test
import utils.toJSONString
import java.net.URI

/**
 * Created by andrey on 4/9/18.
 */
class AdminControllerTests: WSEchoSocketDelegate {

    val app = ChatApplication
    override var webSocketResponse: String = ""
    val parser = JSONParser()
    val sortedByName = "";val sotedByNameDesc = ""
    val sortedByDate = "";val sotedByDateDesc = ""
    val sortedByActive = "";val sotedByActive = ""
    var defaultRequest = JSONObject()
    val client = WebSocketClient()
    val ws = SimpleEchoSocket()
    lateinit var ws_session: org.eclipse.jetty.websocket.api.Session


    fun getFieldsString(collection: JSONArray, fieldName:String):String {
        var resultArray = ArrayList<String>()
        for (obj in collection) {
            val model = obj as? JSONObject
            if (model!=null && model[fieldName]!=null) {
                resultArray.add(model[fieldName].toString())
            }
        }
        return resultArray.joinToString(",")
    }

    @Before
    fun setUp() {
        app.dBServer = DB("test")
        app.dBServer.db.getCollection("rooms").deleteMany(Document())
        app.dBServer.db.getCollection("users").deleteMany(Document())
        app.dBServer.db.getCollection("sessions").deleteMany(Document())
        app.rooms = Rooms(app.dBServer.db, "rooms")
        app.users = Users(app.dBServer.db, "users")
        app.sessions = Sessions(app.dBServer.db, "sessions")
        app.webServer = Javalin.create()
        app.msgServer = MessageCenter
        app.host = "http://localhost"
        app.port = 8081
        app.webServer.port(app.port)
        app.msgServer.setup()
        var room = Room(app.dBServer.db,"rooms")
        room["_id"] = "r1";room["name"] = "Room 1"
        app.rooms.addModel(room)
        var user = User(app.dBServer.db,"users")
        user["login"] = "Andrey";user["birthDate"] = 1045;user["active"] = true;user["role"]=2;user["_id"]="12345"
        user.save() {};
        app.users.addModel(user)
        var session = Session(app.dBServer.db,"sessions",user)
        session["_id"] = "12345";session["user_id"] = "12345";session["room"] = "r1";
        session["loginTime"] = (System.currentTimeMillis()/1000).toInt()
        session["lastActivityTime"] = session["loginTime"]!!
        session.save{}
        app.sessions.addModel(session)

        user = User(app.dBServer.db,"users");user["_id"]="1";
        user["login"] = "arnold";user["birthDate"] = 2;user["active"] = true;user.save() {};app.users.addModel(user)
        session = Session(app.dBServer.db,"sessions",user);session["_id"] = "1"
        session["user_id"] = "1";session["room"] = "r1";session.save{};app.sessions.addModel(session)
        user = User(app.dBServer.db,"users");user["_id"]="2";
        user["login"] = "143Man";user["birthDate"] = 6453;user["active"] = false;user.save{};app.users.addModel(user)
        session = Session(app.dBServer.db,"sessions",user);session["_id"] = "2"
        session["user_id"] = "2";session["room"] = "r1";session.save{};app.sessions.addModel(session)
        user = User(app.dBServer.db,"users");user["_id"]="3";
        user["login"] = "Ben";user["birthDate"] = 9321124;user["active"] = false;user.save{};app.users.addModel(user)
        session = Session(app.dBServer.db,"sessions",user);session["_id"] = "3"
        session["user_id"] = "3";session["room"] = "r1";session.save{};app.sessions.addModel(session)
        user = User(app.dBServer.db,"users");user["_id"]="4";
        user["login"] = "john";user["birthDate"] = 9321124;user["active"] = true;user.save{};app.users.addModel(user)
        session = Session(app.dBServer.db,"sessions",user);session["_id"] = "4"
        session["user_id"] = "4";session["room"] = "r1";session.save{};app.sessions.addModel(session)
        user = User(app.dBServer.db,"users");user["_id"]="5";
        user["login"] = "Josh";user["birthDate"] = 24435;user["active"] = true;user.save{};app.users.addModel(user)
        session = Session(app.dBServer.db,"sessions",user);session["_id"] = "5"
        session["user_id"] = "5";session["room"] = "r1";session.save{};app.sessions.addModel(session)
        defaultRequest = JSONObject(mapOf(
                "request_id" to "12345",
                "user_id" to "12345",
                "session_id" to "12345"
        ))
    }

    @Test
    fun get_users_list_without_filters() {
        var request = defaultRequest
        request["action"] = "admin_get_users_list"
        var response = AdminController.admin_get_users_list.exec(request)
        var list = response["list"] as JSONArray
        assertEquals("Should return all users collection",6,list.count())
        assertEquals("First item should be in natural sort order","Andrey",(list[0] as JSONObject)["login"].toString())
    }

    @Test
    fun get_users_list_offset() {
        var request = defaultRequest
        request["action"] = "admin_get_users_list"
        request["offset"] = 2
        var response = AdminController.admin_get_users_list.exec(request)
        var list = response["list"] as JSONArray
        assertEquals("Should return limited users collection",4,list.count())
        assertEquals("Should return correct fist item after apply offset","143Man",(list[0] as JSONObject)["login"].toString())
        assertEquals("Should return correct last item after apply offset","Josh",(list[list.count()-1] as JSONObject)["login"].toString())
    }

    @Test
    fun get_users_list_limit() {
        var request = defaultRequest
        request["action"] = "admin_get_users_list"
        request["limit"] = 3
        var response = AdminController.admin_get_users_list.exec(request)
        var list = response["list"] as JSONArray
        assertEquals("Should return limited users collection",3,list.count())
        assertEquals("Should return correct fist item after apply offset","Andrey",(list[0] as JSONObject)["login"].toString())
        assertEquals("Should return correct last item after apply offset","143Man",(list[list.count()-1] as JSONObject)["login"].toString())
    }

    @Test
    fun get_users_list_offset_limit() {
        var request = defaultRequest
        request["action"] = "admin_get_users_list"
        request["offset"] = 2
        request["limit"] = 3
        var response = AdminController.admin_get_users_list.exec(request)
        var list = response["list"] as JSONArray
        assertEquals("Should return limited users collection",3,list.count())
        assertEquals("Should return correct fist item after apply offset","143Man",(list[0] as JSONObject)["login"].toString())
        assertEquals("Should return correct last item after apply offset","john",(list[list.count()-1] as JSONObject)["login"].toString())
        request["limit"] = 10
        response = AdminController.admin_get_users_list.exec(request)
        list = response["list"] as JSONArray
        assertEquals("Should return limited users collection",4,list.count())
        assertEquals("Should return correct last item after apply offset","Josh",(list[list.count()-1] as JSONObject)["login"].toString())
    }

    @Test
    fun get_users_list_filter() {
        var request = defaultRequest
        request["action"] = "admin_get_users_list"
        request["filter"] = "gdfgdf"
        var response = AdminController.admin_get_users_list.exec(request)
        var list = response["list"] as JSONArray
        assertEquals("Should return nothing",0,list.count())
        request["filter"] = "a"
        response = AdminController.admin_get_users_list.exec(request)
        list = response["list"] as JSONArray
        assertEquals("Should return correct number of items in case insensitive filter",2,list.count())
        request["filter"] = "jo"
        response = AdminController.admin_get_users_list.exec(request)
        list = response["list"] as JSONArray
        assertEquals("Should return correct number of items in case insensitive filter",2,list.count())
        request["filter"] = "2"
        response = AdminController.admin_get_users_list.exec(request)
        list = response["list"] as JSONArray
        assertEquals("Should return correct number of items by filtering Integer field",3,list.count())
        request["fields"] = "[\"login\",\"birthDate\"]"
        response = AdminController.admin_get_users_list.exec(request)
        list = response["list"] as JSONArray
        assertEquals("Should return correct number of items by filtering only subset of fields",2,list.count())
        request["fields"] = ArrayList<String>()
        response = AdminController.admin_get_users_list.exec(request)
        assertEquals("Should return error by filtering in empty fields list","error",response["status"].toString())
    }

    @Test
    fun get_users_list_sort() {
        var request = defaultRequest
        request["action"] = "admin_get_users_list"
        request["sort"] = "{\"login\":\"ASC\"}"
        var response = AdminController.admin_get_users_list.exec(request)
        var list = response["list"] as JSONArray
        assertEquals("Should sort strings correctly in ascending order case insensitive",
                "143Man,Andrey,arnold,Ben,john,Josh",getFieldsString(list,"login"))
        request["sort"] = "{\"login\":\"DESC\"}"
        response = AdminController.admin_get_users_list.exec(request)
        list = response["list"] as JSONArray
        assertEquals("Should sort strings correctly in descending order case insensitive",
                "Josh,john,Ben,arnold,Andrey,143Man",getFieldsString(list,"login"))
        request["sort"] = "{\"birthDate\":\"ASC\"}"
        response = AdminController.admin_get_users_list.exec(request)
        list = response["list"] as JSONArray
        assertEquals("Should sort Integers correctly in asscending order",
                "2,1045,6453,24435,9321124,9321124",getFieldsString(list,"birthDate"))
        request["sort"] = "{\"birthDate\":\"DESC\"}"
        response = AdminController.admin_get_users_list.exec(request)
        list = response["list"] as JSONArray
        assertEquals("Should sort Integers correctly in descending order",
                "9321124,9321124,24435,6453,1045,2",getFieldsString(list,"birthDate"))
        request["sort"] = "{\"active\":\"ASC\"}"
        response = AdminController.admin_get_users_list.exec(request)
        list = response["list"] as JSONArray
        assertEquals("Should sort Booleans correctly in ascending order",
                "false,false,true,true,true,true",getFieldsString(list,"active"))
        request["sort"] = "{\"active\":\"DESC\"}"
        response = AdminController.admin_get_users_list.exec(request)
        list = response["list"] as JSONArray
        assertEquals("Should sort Booleans correctly in descending order",
                "true,true,true,true,false,false",getFieldsString(list,"active"))
        request["sort"] = "{\"active\":\"WEIRD\"}"
        response = AdminController.admin_get_users_list.exec(request)
        assertEquals("Should return error if sort order specified is not ASC or DESC",
                "error",response["status"].toString())
        request["sort"] = "{\"booctive\":\"ASC\"}"
        response = AdminController.admin_get_users_list.exec(request)
        list = response["list"] as JSONArray
        assertEquals("Should return list in natural order if incorrect sort field provided",
                "Andrey,arnold,143Man,Ben,john,Josh",getFieldsString(list,"login"))
    }

    @Test
    fun get_users_list_external_full_cycle() {
        app.webServer.start()
        ws.delegate = this
        client.start()
        var con = client.connect(ws, URI("ws://localhost:"+app.port+"/websocket"))
        ws_session = con.get()
        var request = defaultRequest
        request["action"] = "admin_get_users_list"
        request["sort"] = JSONObject(mapOf("birthDate" to "ASC"))
        var fields = JSONArray();fields.add("login");fields.add("birthDate");fields.add("first_name");fields.add("last_name")
        request["fields"] = fields
        request["offset"] = 2
        request["limit"] = 3
        request["filter"] = "JO"
        ws_session.remote.sendString(toJSONString(request))
        Thread.sleep(500)
        val response = parser.parse(this.webSocketResponse) as JSONObject
        var list = JSONArray()
        if (response["list"] is String) {
            list = parser.parse(response["list"].toString()) as JSONArray
        } else if (response["list"] is JSONArray){
            list = response["list"] as JSONArray
        }
        assertEquals("Should return list with correct number of items after applying all conditions",2,list.count())
        assertEquals("Should return items with correct sort order","Josh",(list[0] as JSONObject)["login"].toString())
    }

    @Test
    fun remove_users_external_full_cycle() {
        app.webServer.start()
        ws.delegate = this
        client.start()
        var con = client.connect(ws, URI("ws://localhost:"+app.port+"/websocket"))
        ws_session = con.get()
        var request = defaultRequest
        request["action"] = "admin_remove_users"
        ws_session.remote.sendString(toJSONString(request))
        Thread.sleep(500)
        var response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should return error if user list is not provided","error",response["status"].toString())
        request["list"] = "something weird"
        ws_session.remote.sendString(toJSONString(request))
        Thread.sleep(500)
        response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should return error if incorrect user list provided","error",response["status"].toString())
        var list = JSONArray()
        list.add("25");list.add("32")
        request["list"] = list
        ws_session.remote.sendString(toJSONString(request))
        Thread.sleep(500)
        response = parser.parse(this.webSocketResponse) as JSONObject
        assertEquals("Should not remove users which does not exist ",0,response["count"].toString().toInt())
        list = JSONArray()
        list.add("3")
        list.add("2")
        list.add("12345")
        request["list"] = list
        ws_session.remote.sendString(toJSONString(request))
        Thread.sleep(500)
        assertEquals("Should remove users excluding myself from database",4,app.dBServer.db.getCollection("users").find().count())
        assertEquals("Should remove sessions excluding myself from database",4,app.dBServer.db.getCollection("sessions").find().count())
        assertEquals("Should remove users excluding myself from collection",4,app.users.count())
        assertEquals("Should remove sessions excluding myself from collection",4,app.sessions.count())
    }
}