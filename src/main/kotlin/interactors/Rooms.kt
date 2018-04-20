/**
 * Created by andrey on 2/20/18.
 */
package interactors

import com.mongodb.client.MongoDatabase
import org.bson.Document
import models.Room

/**
 * Class represents iterable collection of database models of chat rooms.
 *
 * @param db Link to MongoDB database instance
 * @param colName Name of collection which contains rooms in database
 */
class Rooms(db: MongoDatabase, colName:String): DBCollection(db,colName) {

    /**
     * Schema of database models
     */
    override var schema = hashMapOf(
            "_id" to "String",
            "name" to "String"
    )

    /**
     * Used to load model from JSON document, using correct model type for this
     *
     * @param doc JSON document
     */
    override fun addItem(doc: Document, schema: java.util.HashMap<String, String>?) {
        Room(db, collectionName).addFromJSON(doc,this)
    }
}
