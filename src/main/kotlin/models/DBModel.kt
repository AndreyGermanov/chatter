package models

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.UpdateOptions
import core.ChatApplication
import kotlinx.coroutines.experimental.launch
import org.bson.Document
import org.bson.types.ObjectId
import org.omg.CORBA.Object

/*
*   Interface, which must implement all database models
*/
interface AbstractDBModel {
    operator fun get (index:String): Any?
    operator fun set (index:String,value:Any)
    fun save (callback:()->Unit)
    fun load (callback:(result:Boolean)->Unit)
    fun getDBSchema(): HashMap<String,String>
}


/*
* Base class for database record in MongoDB collection named [colName]
* in [db] database
*/
class DBModel(db:MongoDatabase,colName:String): AbstractDBModel {

    private var doc = Document()
    var schema = HashMap<String,String>()
    var collectionName:String
    var db:MongoDatabase

    init {
        collectionName = colName
        this.db = db
    }

    /*
    *  Operator which returns field of record in format record[[index]]
    */
    override operator fun get (index:String) : Any? {
        if (doc.contains(index)) {
            return doc.get(index)
        } else {
            return null
        }
    }

    /*
    * Operator to set field of record in format record[[index]]
    */
    override operator fun set(index:String,value:Any) {
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

    /*
    * Operator which inserts or updates current record in database in async mode
    * and runs [callback] after this
    */
    override fun save(callback:()->Unit) {
        var col = db.getCollection(collectionName)
        col.updateOne(Document("_id", doc.get("_id")), Document("\$set", doc), UpdateOptions().upsert(true))
        callback()
    }

    /*
    * Operator which loads current record from database, using "_id" field of current document
    * and returns [callback] with result of operation in [result] field.
    */
    override fun load(callback: (result:Boolean) -> Unit) {
        var col = db.getCollection(collectionName)
        if (doc.contains("_id")) {
            val result = col.find(Document("_id",doc.get("_id")))
            if (result.count()!=1) {
                callback(false)
            } else {
                doc = result.first()
            }
        }
    }

    /*
    *   Returns schema of this model
    */
    override fun getDBSchema(): HashMap<String,String> {
        return this.schema
    }
}