/**
 * Created by Andrey Germanov on 4/20/18.
 */
package interactors

import com.mongodb.client.MongoDatabase
import org.bson.Document
import models.Message

/**
 * Collection of Chat messages
 *
 * @param db Link to MongoDB database instance
 * @param colName Name of collection which contains messages in database
 */
class Messages(db: MongoDatabase, colName:String): DBCollection(db,colName) {

    /**
     * Collection schema definition:
     * _id - Unique id of row
     * timestamp - date and time of message
     * from_user_id - ID of User model, which created this message
     * to_room_id - ID of Room model, to which message sent (optional)
     * to_user_id - ID of User model, to which message sent (optional)
     * text - text of message (optional)
     * "attachment_id" - ID of file, which is attached to this message (optional)
     *
     * Each message should contain either "to_room_id" if it public message, or "to_user_id" if it private message,
     * but not both.
     * Also, each message can contain "text" or "attachment_id", or both if message is text with attached file
     */
    override var schema = hashMapOf(
            "_id" to "String",
            "timestamp" to "Int",
            "from_user_id" to "String",
            "to_room_id" to "String",
            "to_user_id" to "String",
            "text" to "String",
            "attachment_id" to "String"
    )

    /**
     * Used to load model from JSON document, using correct model type for this
     *
     * @param doc JSON document
     * @param schema Schema of document for validation
     */
    override fun addItem(doc: Document, schema: java.util.HashMap<String, String>?) {
        Message(db, collectionName).addFromJSON(doc,this)
    }
}



