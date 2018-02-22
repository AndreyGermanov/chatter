/**
 * Created by andrey on 2/19/18.
 */

package interactors

import com.mongodb.client.MongoDatabase
import core.ChatApplication
import models.DBModel
import org.bson.Document
import models.Session
import models.User

class Sessions(db:MongoDatabase,colName:String): DBCollection(db,colName) {

    /**
     * Schema of database models
     */
    override var schema = mapOf(
            "_id" to "String",
            "user_id" to "String",
            "loginTime" to "Int",
            "lastActivityTime" to "Int",
            "room" to "String"
    ) as HashMap<String,String>

    /**
     * Function reads/rereads list of sessions from underlying MongoDB collection.
     *
     * @param condition Query condition for MongoDB
     * @return callback after operation completed
     */
    override fun loadList(condition:Document?,callback:()->Unit) {
        super.schema = this.schema
        super.loadList(condition) {
            var filtered_models = ArrayList<Any>()
            models = models.filterTo(filtered_models) {
                val session = it as DBModel
                if (session["user_id"]!=null) {
                    val room = app.rooms.getById(session["room"].toString())
                    ChatApplication.users.getById(session["user_id"].toString()) != null && room != null
                } else {
                    false
                }
            }
            models = filtered_models
            callback()
        }
    }

    /**
     * Adds session to list of sessions, if session for this user_id not exists
     *
     * @param model Session model to add
     */
    override fun addModel(model:Any) {
        var session = model as DBModel
        if (ChatApplication.users.getBy("user_id",session["user_id"].toString())==null) {
            super.addModel(model)
        }
    }

    /**
     * Used to load model from JSON document, using correct model type for this
     *
     * @param doc JSON document
     */
    override fun addItem(doc: Document, schema: java.util.HashMap<String, String>?) {
        if (doc.contains("user_id")) {
            var obj = app.users.getById(doc.get("user_id").toString())
            if (obj != null) {
                val user = obj as User
                obj = this.getBy("user_id",doc.get("user_id").toString())
                if (obj!=null) {
                    val session = obj as Session
                    if (session["_id"] != doc.get("_id")) {
                        return
                    }
                }
                Session(db, collectionName, user as User).addFromJSON(doc, this)
            }
        }
    }
}
