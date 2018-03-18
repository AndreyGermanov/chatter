package interactors

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import core.ChatApplication
import core.DB
import models.Room
import models.Session
import models.User
import org.bson.Document
import org.json.simple.JSONObject
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import java.util.*

/**
 * Created by andrey on 2/21/18.
 */
class UsersTest {

    lateinit var dbcol: Users
    lateinit var col: MongoCollection<Document>
    var app = ChatApplication
    lateinit var db: MongoDatabase

    @Before
    fun startUp() {
        app.dBServer = DB("test")
        db = app.dBServer.db
        app.users = Users(db,"users")
        app.sessions = Sessions(db,"sessions")
        app.rooms = Rooms(db,"rooms")
        db.getCollection("users").deleteMany(Document())
        db.getCollection("sessions").deleteMany(Document())
        db.getCollection("rooms").deleteMany(Document())
        dbcol = app.users
        col = db.getCollection("users")
        val user1 = User(db,"users")
        user1["login"] = "login1"
        user1["password"] = "Unencrypted"
        user1["email"] = "test@test.com"
        user1.save{}
        val user2 = User(db,"users")
        user2["login"] = "login2"
        user2["password"] = "UNPASS"
        user2["email"] = "test3@test.com"
        user2.save{}
        val room1 = Room(db,"rooms")
        room1["name"] = "Room 1"
        room1.save{}
        val room2 = Room(db,"rooms")
        room2["name"] = "Room 2"
        room2.save{}
    }

    @Test
    fun register() {
        dbcol.loadList(null) {
            val params = JSONObject()
            dbcol.register(params) { result,user ->
                assertEquals("Should not register if login not specified",Users.UserRegisterResultCode.RESULT_ERROR_NO_LOGIN,result)
                params.set("login","")
                dbcol.register(params) { result,user ->
                    assertEquals("Should not register if login is empty",Users.UserRegisterResultCode.RESULT_ERROR_NO_LOGIN,result)
                    params.set("login","login1")
                    dbcol.register(params) { result,user ->
                        assertEquals("Should not register if email is not specified",Users.UserRegisterResultCode.RESULT_ERROR_NO_EMAIL,result)
                        params.set("email","")
                        dbcol.register(params) { result,user ->
                            assertEquals("Should not register if email is empty", Users.UserRegisterResultCode.RESULT_ERROR_NO_EMAIL,result)
                            params.set("email","test@test.com")
                            dbcol.register(params) { result,user ->
                                assertEquals("Should not register if password is not specified",Users.UserRegisterResultCode.RESULT_ERROR_NO_PASSWORD,result)
                                params.set("password","pass")
                                dbcol.register(params) { result,user ->
                                    assertEquals("Should not register if login already exists",Users.UserRegisterResultCode.RESULT_ERROR_LOGIN_EXISTS,result)
                                    params.set("login","andrey")
                                    dbcol.register(params) { result,user ->
                                        assertEquals("Should not register if email already exists", Users.UserRegisterResultCode.RESULT_ERROR_EMAIL_EXISTS,result)
                                        params.set("email","andrey@it-port.ru")
                                        dbcol.register(params) { result,user ->
                                            assertEquals("Should be registered",Users.UserRegisterResultCode.RESULT_OK,result)
                                            assertNotNull("Should return user instance",user)
                                            assertNotNull("Should have _id",user!!["_id"])
                                            assertFalse("Should not be activated",user!!["active"] as Boolean)
                                            assertEquals("User written to database",1,col.find(Document("_id",user!!["_id"].toString())).count())
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun activate() {
        val obj = JSONObject()
        obj.set("login","andrey")
        obj.set("password","12345")
        obj.set("email","andrey@it-port.ru")
        app.users.register(obj) { result,user ->
            if (result == Users.UserRegisterResultCode.RESULT_OK) {
                if (user != null) {
                    var activation_code = user["_id"] as String
                    app.users.activate("12345") { result ->
                        assertEquals("Should not activate user with incorrect token",Users.UserActivationResultCode.RESULT_ERROR_NO_USER,result)
                        app.users.activate(activation_code) { result ->
                            assertEquals("Should activate registered user with correct token",Users.UserActivationResultCode.RESULT_OK,result)
                            app.users.activate(activation_code) { result ->
                                assertEquals("Should not activate registered user twice",Users.UserActivationResultCode.RESULT_ERROR_USER_ALREADY_ACTIVATED,result)
                            }
                        }
                    }
                } else {
                    fail("User not registered")
                }
            } else {
                fail("Could not register user for activation")
            }
        }
    }

    @Test
    fun login() {
        val obj = JSONObject()
        obj.set("login","andrey")
        obj.set("password","testtest")
        obj.set("email","andrey@it-port.ru")
        app.users.register(obj) { result,user ->
            if (result == Users.UserRegisterResultCode.RESULT_OK) {
                val reg_user = user
                app.users.login("a","b") { result,user ->
                    assertEquals("Should not login user with incorrect login",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN,result)
                    app.users.login("andrey","b") { result,user ->
                        assertEquals("Should not login user with incorrect password",Users.UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD,result)
                        app.users.login("andrey","testtest") { result,user ->
                            assertEquals("Should not login user without activation",Users.UserLoginResultCode.RESULT_ERROR_NOT_ACTIVATED,result)
                            app.users.activate(reg_user!!["_id"] as String) { result ->
                                if (result == Users.UserActivationResultCode.RESULT_OK) {
                                    app.users.login("andrey","testtest") { result,user ->
                                        assertEquals("Should login user with correct login and password",Users.UserLoginResultCode.RESULT_OK,result)
                                        assertNotNull("Should add user to users collection",app.users.getById(user!!["_id"].toString()))
                                        assertEquals("Should write user to database",1,db.getCollection("users").find(Document("_id",user!!["_id"].toString())).count())
                                        assertNotNull("Should create session for login user",app.sessions.getBy("user_id",user!!["_id"].toString()))
                                        assertEquals("Should write session to database",1,db.getCollection("sessions").find(Document("user_id",user!!["_id"].toString())).count())
                                        app.users.login("andrey","testtest") { result,user ->
                                            assertEquals("Should not allow to login user second time right after first login",Users.UserLoginResultCode.RESULT_ERROR_ALREADY_LOGIN,result)
                                            assertEquals("Should not create secondary section",1,app.sessions.count())
                                            var session = app.sessions.getBy("user_id",reg_user!!["_id"].toString()) as Session
                                            val lastActivityTime = session["lastActivityTime"].toString().toInt()
                                            Thread.sleep(20*1000)
                                            app.users.login("andrey","testtest") { result,user ->
                                                assertEquals("Should login user again if inativity period of previous session exceeded",Users.UserLoginResultCode.RESULT_OK,result)
                                                assertEquals("Should not add second session for user",1,app.sessions.count())
                                                assertTrue("Should update session activity time",lastActivityTime<session["lastActivityTime"].toString().toInt())
                                            }
                                        }
                                    }
                                } else {
                                    fail("Could not activate user for login")
                                }
                            }
                        }
                    }
                }
            } else {
                fail("Could not register user for login")
            }
        }

    }

    @Test
    fun updateUser() {
        app.rooms.loadList(null){}
        app.users.loadList(null){}
        val obj = JSONObject()
        app.users.updateUser(obj) { result,message ->
            assertEquals("Should not be empty request",Users.UserUpdateResultCode.RESULT_ERROR_USER_NOT_SPECIFIED,result)
            obj.set("first_name","")
            app.users.updateUser(obj) { result,message ->
                assertEquals("Should contain user_id field",Users.UserUpdateResultCode.RESULT_ERROR_USER_NOT_SPECIFIED,result)
                obj.set("user_id","NO")
                app.users.updateUser(obj) { result,message ->
                    assertEquals("Should contain correct user_id",Users.UserUpdateResultCode.RESULT_ERROR_USER_NOT_FOUND,result)
                    val user = app.users.getBy("login","login1") as User
                    val user_id = user!!["_id"].toString()
                    obj.set("user_id",user_id)
                    obj.remove("first_name")
                    app.users.updateUser(obj) { result,message ->
                        assertEquals("Should success if some fields not provided",Users.UserUpdateResultCode.RESULT_OK,result)
                        obj.set("password","")
                        app.users.updateUser(obj) { result, message ->
                            assertTrue("Should contain correct password", result == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY && message == "password")
                            obj.set("password","12345")
                            obj.set("confirm_password","1234")
                            app.users.updateUser(obj) { result, message ->
                                assertTrue("Password and confirm password should match", result == Users.UserUpdateResultCode.RESULT_ERROR_PASSWORDS_SHOULD_MATCH && message == "password")
                                obj.set("confirm_password","12345")
                                obj.set("first_name", "")
                                app.users.updateUser(obj) { result, message ->
                                    assertTrue("Should contain correct first_name", result == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY && message == "first_name")
                                    obj.set("first_name", "Bob")
                                    obj.set("last_name", "")
                                    app.users.updateUser(obj) { result, message ->
                                        assertTrue("Should contain correct last_name", result == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY && message == "last_name")
                                        obj.set("last_name", "Brown")
                                        obj.set("gender", "")
                                        app.users.updateUser(obj) { result, message ->
                                            assertTrue("Should contain correct gender", result == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY && message == "gender")
                                            obj.set("gender", "widget")
                                            app.users.updateUser(obj) { result, message ->
                                                assertTrue("Should contain correct gender", result == Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE && message == "gender")
                                                obj.set("gender", "M")
                                                obj.set("birthDate", "")
                                                app.users.updateUser(obj) { result, message ->
                                                    assertTrue("Should contain correct birthDate", result == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY && message == "birthDate")
                                                    obj.set("birthDate", "too old to remember")
                                                    app.users.updateUser(obj) { result, message ->
                                                        assertTrue("Should contain correct birthDate", result == Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE && message == "birthDate")
                                                        obj.set("birthDate", 0)
                                                        app.users.updateUser(obj) { result, message ->
                                                            assertTrue("Should contain correct birthDate", result == Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE && message == "birthDate")
                                                            obj.set("birthDate", 9999999999 * 100)
                                                            app.users.updateUser(obj) { result, message ->
                                                                assertTrue("Should contain correct birthDate", result == Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE && message == "birthDate")
                                                                var calendar = Calendar.getInstance()
                                                                calendar.set(1981, 9, 18)
                                                                obj.set("birthDate", (calendar.timeInMillis / 1000).toInt())
                                                                obj.set("default_room", "")
                                                                app.users.updateUser(obj) { result, message ->
                                                                    assertTrue("Should contain correct default room", result == Users.UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY && message == "default_room")
                                                                    obj.set("default_room", "NO ROOM totally")
                                                                    app.users.updateUser(obj) { result, message ->
                                                                        assertTrue("Should contain correct default room", result == Users.UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE && message == "default_room")
                                                                        val room = app.rooms.getBy("name", "Room 1")
                                                                        val room_id = room!!["_id"]
                                                                        obj.set("default_room", room_id)
                                                                        app.users.updateUser(obj) { result, message ->
                                                                            assertEquals("Should accept update if parameters are ok", Users.UserUpdateResultCode.RESULT_OK, result)
                                                                            val obj = col.find(Document("login", "login1")).first()
                                                                            val db_first_name = obj.get("first_name").toString()
                                                                            val db_last_name = obj.get("last_name").toString()
                                                                            val db_gender = obj.get("gender").toString()
                                                                            val db_birthDate = obj.get("birthDate").toString().toInt()
                                                                            var db_default_room = obj.get("default_room").toString()
                                                                            val usr = app.users.getBy("login", "login1") as User
                                                                            val col_first_name = usr.get("first_name").toString()
                                                                            val col_last_name = usr.get("last_name").toString()
                                                                            val col_gender = usr.get("gender").toString()
                                                                            val col_birthDate = usr.get("birthDate").toString().toInt()
                                                                            var col_default_room = usr.get("default_room").toString()
                                                                            assertTrue("Should have correct first_name", db_first_name == col_first_name && col_first_name == "Bob")
                                                                            assertTrue("Should have correct last_name", db_last_name == col_last_name && col_last_name == "Brown")
                                                                            assertTrue("Should have correct gender", db_gender == col_gender && col_gender == "M")
                                                                            assertTrue("Should have correct birthDate", db_birthDate == col_birthDate && col_birthDate == (calendar.timeInMillis / 1000).toInt())
                                                                            assertTrue("Should have correct default_room", db_default_room == col_default_room && col_default_room == room_id)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}