package interactors

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import core.ChatApplication
import core.DB
import models.Room
import models.Session
import models.User
import org.bson.Document
import org.junit.After
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before

/**
 * Created by andrey on 2/21/18.
 */
class SessionsTest {

    val app = ChatApplication
    lateinit var db: MongoDatabase
    lateinit var dbcol: Sessions
    lateinit var col: MongoCollection<Document>

    @Before
    fun startUp() {
        app.dBServer = DB("test")
        db = app.dBServer.db
        app.rooms = Rooms(db,"rooms")
        app.users = Users(db,"users")
        app.sessions = Sessions(db,"sessions")
        col = db.getCollection("sessions")
        dbcol = app.sessions
        db.getCollection("users").deleteMany(Document())
        db.getCollection("rooms").deleteMany(Document())
        db.getCollection("sessions").deleteMany(Document())
        var room1 = Room(db,"rooms")
        room1["_id"] = "r1"
        room1["name"] = "room1"
        var room2 = Room(db,"rooms")
        room2["_id"] = "r2"
        room2["name"] = "Room 2"

        val user1 = User(db,"users")
        user1["_id"] = "u1"
        user1["login"] = "test1@test.com"
        user1["password"] = "Unencrypted"
        user1["email"] = "test1@test.com"
        user1["default_room"] = "r1"
        val user2 = User(db,"users")
        user2["_id"] = "u2"
        user2["login"] = "User2"
        user2["password"] = "NOT ENCRYPTED"
        user2["email"] = "test2@test.com"
        user2["default_room"] = "r2"
        room1.save {}
        room2.save {}
        user1.save {}
        user2.save {}
        app.users.addModel(user1)
        app.users.addModel(user2)
        app.rooms.addModel(room1)
        app.rooms.addModel(room2)
        val session1 = Session(db,"sessions",user1)
        session1["_id"] = "s1"
        session1["room"] = "r1"
        val session2 = Session(db,"sessions",user2)
        session2["_id"] = "s2"
        session2["room"] = "r2"
        session1.save{}
        session2.save{}
        app.users.detach("u1")
        app.users.detach("u2")
        app.rooms.detach("r1")
        app.rooms.detach("r2")
    }

    @After
    fun shutDown() {
    }

    @Test
    fun loadList() {
        println(dbcol.count())
        for (it in dbcol) {
            println(it)
        }
        dbcol.loadList(null) {
            for (it in dbcol) {
                println(it)
            }
            assertEquals("Should not load sessions if users not loaded",0,dbcol.count())
            app.users.loadList(null) {
                dbcol.loadList(null) {
                    assertEquals("Should not load sessions if rooms not loaded",0,dbcol.count())
                    app.rooms.loadList(null) {
                        dbcol.loadList(null) {
                            assertEquals("Should load all sessions if rooms and users loaded properly", 2, dbcol.count())
                            val tmp_user = app.users.getById("u1") as User
                            app.users.remove("u1") {
                                dbcol.loadList(null) {
                                    assertEquals("Should not load sessions with non exist users",1,dbcol.count())
                                    app.users.addModel(tmp_user)
                                    val tmp_room = app.rooms.getById("r2") as Room
                                    app.rooms.remove("r2") {
                                        dbcol.loadList(null) {
                                            assertEquals("Should not load sessions with non exist rooms", 1, dbcol.count())
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
    fun addModel() {
        app.rooms.loadList(null) {
            app.users.loadList(null) {
                val session = Session(db, "sessions", app.users.getById("u1") as User)
                session["_id"] = "s1"
                dbcol.addModel(session)
                assertEquals("Should add first model to sessions collection", 1, dbcol.count())
                val session2 = Session(db, "sessions", app.users.getById("u2") as User)
                dbcol.addModel(session2)
                assertEquals("Should add second model to sessions collection", 2, dbcol.count())
                val session3 = Session(db, "sessions", app.users.getById("u1") as User)
                dbcol.addModel(session2)
                assertEquals("Should not add duplicate model to sessions collection", 2, dbcol.count())
                dbcol.addModel(session3)
                assertEquals("Should not add session model with duplicate user id to sessions collection", 2, dbcol.count())
            }
        }
    }

    @Test
    fun addItem() {
        app.rooms.loadList(null) {
            app.users.loadList(null) {
                val doc = Document()
                dbcol.addItem(doc)
                assertEquals("Should not create model based on empty document", 0, dbcol.count())
                doc.set("user_id","no_user")
                dbcol.addItem(doc)
                assertEquals("Should not create model with non exist user",0, dbcol.count())
                doc.set("user_id","u1")
                doc.set("room","no_room")
                dbcol.addItem(doc)
                assertEquals("Should not create model with non exist room", 0, dbcol.count())
                doc.set("room","r1")
                dbcol.addItem(doc)
                assertEquals("Should create model if all fields complete successfuly",1,dbcol.count())
                doc.set("_id",12345)
                dbcol.addItem(doc)
                assertEquals("Should net create model with duplicate user_id",1,dbcol.count())
            }
        }
    }

}