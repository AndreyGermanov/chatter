package interactors

import com.mongodb.client.MongoDatabase
import org.bson.Document
import models.User

/**
 * Created by Andrey Germanov on 2/18/18.
 */

/**
 * Class represents iterable collection of chat users, based on MongoDB collection
 *
 * @param db Link to MongoDB database
 * @param colName Name of collection in MongoDB database
 */
class Users(db: MongoDatabase, colName:String = "users"): DBCollection(db,colName) {

    /**
     * Used to load model from JSON document, using correct model type for this
     *
     * @param doc JSON document
     */
    override fun addItem(doc: Document, schema: HashMap<String,String>?) {
        User(db, collectionName).addFromJSON(doc,this)
    }
}
