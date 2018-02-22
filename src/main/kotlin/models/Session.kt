/**
 * Created by andrey on 2/19/18.
 */

package models

import com.mongodb.client.MongoDatabase
import core.ChatApplication
import interactors.DBCollection
import org.bson.Document
import java.io.IOException

/**
 * Model which represents Chat User session in database
 *
 * @param db Link to MongoDB database, where session is persisted
 * @param colName Name of collection, used to persist sessions
 *
 * @property schema Schema of session model which controls which fields can exist in database
 * @property user Link to User model, connected to session
 */
class Session(db:MongoDatabase,colName:String="sessions",user:User) : DBModel(db,colName) {

    /**
     * Link to schema of database models from collection
     */
    override var schema = app.sessions.schema

    var user: User

    init {
        this.user = user
        if (user["_id"] != null) {
            this["user_id"] = user["_id"]!!
        } else {
            throw IllegalArgumentException("User not found")
        }
    }

    /**
     * Function fills properties of model from JSON object [doc] and adds resulting object to
     * provied [collection]
     *
     * @param doc source JSON document
     * @param collection destination collection
     */
    override fun addFromJSON(doc: Document,collection: DBCollection ) {
        if (!doc.contains("room")) {
            if (this["room"] == null || this["room"].toString() == "") {
                return
            }
        } else {
            val room = ChatApplication.rooms.getById(doc.get("room").toString())
            if (room == null) {
                return
            }
        }
        if (doc.contains("user_id")) {
            val obj = ChatApplication.users.getById(doc.get("user_id").toString())
            if (obj!=null) {
                this.user = obj as User
            } else {
                doc.remove("user_id")
            }
        }
        super.addFromJSON(doc, collection)
    }

    /**
     * Function used to read/reread model from database
     *
     * @param callback callback function returned with result
     */
    override fun load(callback:(result:Boolean)->Unit) {
        super.load { result ->
            if (this["user_id"] != null) {
                val obj = ChatApplication.users.getById(this["user_id"].toString())
                if (obj != null) {
                    this.user = obj as User
                }
            }
            callback(result)
        }
    }

    /**
     * Save session to list of sessions, only if session for specified user_id not exists
     *
     * @param callback resulted callback after operation
     */
    override fun save(callback:()->Unit) {
        val col = db.getCollection(collectionName)
        var user = app.users.getById(this["user_id"].toString())
        if (user == null) {
            callback()
            return
        }
        if (this["room"].toString()!="" && this["room"] != null) {
            val room = app.rooms.getById(this["room"].toString())
            if (room == null) {
                callback()
                return
            }
        }
        val condition = Document("user_id",this["user_id"])
        condition.set("_id",Document("\$ne",this["_id"]))
        if (col.find(condition).count()==0) {
            super.save(callback)
        } else {
            callback()
        }
    }

}
