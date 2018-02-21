/**
 * Created by andrey on 2/19/18.
 */

package interactors

import com.mongodb.client.MongoDatabase
import core.ChatApplication
import org.bson.Document
import models.Session

class Sessions(db:MongoDatabase,colName:String): DBCollection(db,colName) {

    /**
     * Function reads/rereads list of sessions from underlying MongoDB collection.
     *
     * @param condition Query condition for MongoDB
     * @return callback after operation completed
     */
    override fun loadList(condition:Document?,callback:()->Unit) {
        super.loadList(condition) {
            models = models.filterTo(models) {
                val session = it as Session
                ChatApplication.users.getById(session["user_id"].toString())!=null
            }
            callback()
        }
    }

    /**
     * Adds session to list of sessions, if session for this user_id not exists
     *
     * @param model Session model to add
     */
    override fun addModel(model:Any) {
        val session = model as Session
        if (ChatApplication.users.getBy("user_id",session["user_id"].toString())==null) {
            super.addModel(model)
        }
    }

}
