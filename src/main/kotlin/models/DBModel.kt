package models

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.UpdateOptions
import core.ChatApplication
import interactors.DBCollection
import kotlinx.coroutines.experimental.launch
import org.bson.Document
import org.bson.types.ObjectId
import org.json.simple.JSONObject
import org.omg.CORBA.Object

/**
 * Base wrapper class for database Model, stored in MongoDB database
 *
 * @param db Link to MongoDB database, which holds collection
 * @param colName Name of MongoDB collection
 *
 * @property schema Description of schema of this collection. Schema is HashMap<Field,Type> where Field is field of
 *                    model and Type is Java type of model (Int, String, Boolean, Any)
 * @property doc JSON document, which contains actual collection data
 */
open class DBModel(db:MongoDatabase,colName:String) {

    private var doc = Document()
    /**
     * Link to database schema from collection
     */
    open var schema = DBCollection(db,colName).schema

    var collectionName:String
    var db:MongoDatabase
    var app = ChatApplication

    init {
        collectionName = colName
        this.db = db
    }

    /**
     *  Operator which returns field of record in format record[index]
     *
     * @param index Index of field
     * @return field of model specified by inedx
    */
    operator fun get (index:String) : Any? {
        if (doc.contains(index)) {
            return doc.get(index)
        } else {
            return null
        }
    }

    /**
     * Operator to set field of record in format record[index]
     *
     * @param index Index of field
     * @param value Value of field
     */
    operator fun set(index:String,value:Any) {
        if (schema.contains(index)) {
            when (schema.get(index)) {
                "Int" -> doc.set(index, Integer.parseInt(value.toString()))
                "Boolean" -> doc.set(index, value as Boolean)
                "String" -> doc.set(index, value.toString())
                "Long" -> doc.set(index, value.toString().toLong())
                "Double" -> doc.set(index, value.toString().toDouble())
                "Any" -> doc.set(index,value)
            }
        }
    }

    /**
     * Operator which inserts or updates current record in database in async mode
     * and runs [callback] after this
     */
    open fun save(callback:()->Unit) {
        var col = db.getCollection(collectionName)
        var id = doc.get("_id")
        if (id==null) {
            id = ObjectId.get().toString()
        }
        col.updateOne(Document("_id", id), Document("\$set", doc), UpdateOptions().upsert(true))
        callback()
    }

    /**
     * Operator which loads current record from database, using "_id" field of current document
     * and returns [callback] with result of operation in [result] field.
     */
    open fun load(callback: (result:Boolean) -> Unit) {
        var col = db.getCollection(collectionName)
        if (doc.contains("_id")) {
            val result = col.find(Document("_id",doc.get("_id")))
            if (result.count()!=1) {
                callback(false)
            } else {
                doc = result.first()
                callback(true)
            }
        } else {
            callback(false)
        }
    }

    /**
     * Removes current model from MongoDB database
     * @param callback Callback function after complete
     */
    fun remove(callback: () -> Unit) {
        var col = db.getCollection(collectionName)
        col.deleteOne(Document("_id",doc["_id"]))
        callback()
    }

    /**
     *   Returns schema of this model, which defines possible fields and their types
     *   @return HashMap with schema of models of current class
     */
    fun getDBSchema(): HashMap<String,String> {
        return this.schema
    }

    /**
     *  Fills fields of model using provided JSON document and then adds
     *  filled document to provided collection, if it does not exist in it
     *
     *  @param doc Source JSON document
     *  @param collection Destination collection
     */
    open fun addFromJSON(doc:Document,collection: DBCollection) {
        val schema = this.getDBSchema().iterator()
        for (row in schema) {
            if (doc.contains(row.key)) {
                this[row.key] = doc[row.key]!!
            }
        }
        val items = collection.getList()
        if (items.filter {
            val obj = it as DBModel
            obj["_id"] == this["_id"]
        }.count()==0) {
            collection.addModel(this)
        }
    }

    /**
     * Returns string representation of model
     * @return String representation of model
     */
    override fun toString() : String {
        return doc.toString()
    }
}