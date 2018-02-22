package models

import com.mongodb.client.MongoDatabase
import core.ChatApplication
import core.DB
import interactors.Rooms
import interactors.Sessions
import interactors.Users
import org.bson.Document
import org.bson.types.ObjectId
import org.json.simple.JSONObject
import org.junit.After
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before

/**
 * Created by andrey on 2/21/18.
 */
class SessionTest {

    lateinit var db: MongoDatabase
    lateinit var model: Session
    lateinit var dbcol: Sessions
    val app = ChatApplication
    val colName = "sessions"

    @Before
    fun setUp() {

        app.dBServer = DB("test")
        db = app.dBServer.db
        app.sessions = Sessions(db,colName)
        app.rooms = Rooms(db,"rooms")
        app.users = Users(db,"users")
        dbcol = Sessions(db,colName)

        val room = Room(db,"rooms")
        room["_id"] = "r1"
        room["name"] = "Room 1"
        room.save{}
        app.rooms.addModel(room)
        val room2 = Room(db,"rooms")
        room2["_id"] = "r2"
        room2["name"] = "Room 2"
        room2.save{}
        app.rooms.addModel(room2)
        val user = User(db,"users")
        user["_id"] = "u1"
        user["login"] = "myuser"
        user["password"] = "1111"
        user["email"] = "test@test.com"
        user["default_room"] = "r2"
        user.save{}
        app.users.addModel(user)

    }

    @After
    fun tearDown() {
        db.getCollection("users").deleteMany(Document())
        db.getCollection("rooms").deleteMany(Document())
        db.getCollection(colName).deleteMany(Document())
    }

    @Test
    fun addFromJSON() {
        val obj = Document()
        obj.set("_id",ObjectId.get().toString())
        val session = Session(db,colName,app.users.getById("u1") as User)
        session.addFromJSON(obj,dbcol)
        assertEquals("Should not add session to collection, if no room specified",0,dbcol.count())
        obj.set("room","noroom")
        session.addFromJSON(obj,dbcol)
        assertEquals("Should not add session to collection, if specified room does not exist",0,dbcol.count())
        obj.set("room","r1")
        session.addFromJSON(obj,dbcol)
        assertEquals("Should add session to collection, if all options except user_id specified correctly",1, dbcol.count())
        dbcol.remove(session["_id"].toString()) {
            obj.set("user_id","not exist")
            session.addFromJSON(obj,dbcol)
            var sess = dbcol.getById(obj.get("_id").toString()) as Session
            assertNotEquals("Should not add session to collection, if specified user does not exist","not exist",sess["user_id"].toString())
            obj.set("user_id","u1")
            session.addFromJSON(obj,dbcol)
            sess = dbcol.getById(obj.get("_id").toString()) as Session
            assertEquals("Should add session to collection if options specified correctly","u1",sess["user_id"].toString())
        }
    }

    @Test
    fun load() {
        var doc = Document()
        doc.set("_id","12345")
        doc.set("user_id","boboo")
        db.getCollection("sessions").insertOne(doc)
        val user = User(db,"users")
        var session:Session
        try {
            session = Session(db, "sessions", user)
            fail("Should not create setssion object if provided user does not exist in collection")
        } catch (e:IllegalArgumentException) {
        }
        session = Session(db,"sessions",app.users.getById("u1") as User)
        session["_id"] = "12345"
        session.load {
            assertEquals("Should load session record from DB with provided user_id","boboo",session["user_id"].toString())
            assertEquals("Should not create user object for session if user does not exist","u1",session.user["_id"])
            db.getCollection("sessions").updateOne(Document("_id","12345"),Document("\$set",Document("user_id","u1")))
            session.load {
                assertEquals("Should create user object for session if user_id belongs to correct user","u1",session["user_id"].toString())
            }
        }
    }

    @Test
    fun save() {
        var user = app.users.getById("u1") as User
        var session = Session(db,"sessions",user)
        app.users.remove("u1") {
            assertEquals("Session user removed",0,app.users.count())
            session.save {
                assertEquals("Should not save session if session user does not exist",0,db.getCollection("sessions").find().count())
                app.users.addModel(user)
                session["loginTime"] = 12345
                session["lastActiveTime"] = 12325
                session["room"] = "nothing"
                session.save {
                    assertEquals("Should not save session if session room does not exist", 0, db.getCollection("sessions").find().count())
                    session.save {
                        session["room"] = "r1"
                        session.save {
                            assertEquals("Should save session if all data is correct", 1, db.getCollection("sessions").find().count())
                            session["_id"] = "2342"
                            session.save {
                                assertEquals("Should not create second session for the same user", 1, db.getCollection("sessions").find().count())
                            }
                        }
                    }
                }
            }
        }

    }

}