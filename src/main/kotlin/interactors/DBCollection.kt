/**
 * Created by Andrey Germanov on 2/17/18.
 */
package interactors

import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCursor
import com.mongodb.client.MongoDatabase
import core.ChatApplication
import kotlinx.coroutines.experimental.launch
import models.DBModel
import models.User
import org.bson.Document
import org.bson.types.ObjectId
import org.json.simple.JSONArray
import org.json.simple.JSONObject

/**
 *   Base class of collection of database models of type [T] based on DBModel
 *
 *  @param db MongoDB database instance, which holds collection
 *  @param colname Name of MongoDB collection
 *
 *  @property db MongoDB database instance, which holds collection
 *  @property collectionName name of MongoDB collection
 *  @property currentItem index of current collection item in Iterator interface
 *  @property models ArrayList of models, managed by collection
 *  @property schema Default schema of model in this collection (optional)
 * */
open class DBCollection(db:MongoDatabase,colName:String=""): Iterator<Any> {

    var collectionName = ""
    var db:MongoDatabase
    protected var models: ArrayList<Any> = ArrayList<Any>()
    var currentItem = 0

    var schema: HashMap<String,String>? = null

    init {
        collectionName = colName
        this.db = db
    }

    /**
    *   Base function which adds item of specified type to collection from JSON document
    *   using method of Model class, which correctly implements this. Should be overriden in
    *   concrete sublcasses
    *
    *   @param doc JSON document
    */
    open fun addItem(doc:Document,schema: HashMap<String,String>? = null) {
        if (!doc.contains("_id")) {
            doc["_id"] = ObjectId.get()
        }
        val model = DBModel(db,collectionName)
        if (schema != null) {
            model.schema = schema
        } else if (model.schema.count()==0 && this.schema != null) {
            model.schema = this.schema!!
        }
        model.addFromJSON(doc,this)
    }

    /**
    *   Internal method used to add model to collection from external classes
    *
    *   @param model Model to add
    */
    open fun addModel(model:Any) {
        var dbmodel = model as DBModel
        var items = models.filter {
            var item = it as DBModel
            item["_id"] == dbmodel["_id"]
        }
        if (items.count() == 0) {
            models.add(model)
        }
    }

    /**
    *   Function, used to load all documents to collection from MongoDB,
    *   which meets specified conditions, or all documents if condition is null
    *
    *   @param condition filtering condition
    *   @param callback callback function
    */
    open fun loadList(condition: Document?, callback:()->Unit) {
        val col = db.getCollection(collectionName)
        models.clear()
            var result: FindIterable<Document>
            if (condition != null) {
                result = col.find(condition)
            } else {
                result = col.find()
            }
            if (result.count() > 0) {
                val docs = result.iterator()
                for (doc in docs) {
                    addItem(doc)
                }
                callback()
            } else {
                callback()
            }
    }

    /**
     * Returns list of all models in collection
     * @return ArrayList with all models
    */
    fun getList():ArrayList<Any> {
        return this.models
    }

    /**
     * Implementation of next() method of Iterator interface to use collection as Iterator
     * @return Current model in collection
     */
    override fun next(): Any {
        return models[currentItem++]
    }

    /**
     * Implementation of hasNext() method of Iterator interface to use collection as Iterator
     * @return True if collection has next item or False if does not have
     */
    override fun hasNext(): Boolean {
        return currentItem < models.count()
    }

    /**
     * Returns count of items in collection
     * @return Count of models in collection
     */
    fun count(): Int {
        return models.count()
    }

    /**
     * Returns model by index
     * @param index Index of model in collection
     * @return Model with specified index
     */
    fun getByIndex(index:Int): Any? {
        if (models.count()>index) {
            return models[index]
        } else {
            return null
        }
    }

    /**
     *  Returns model by specified ID
     *  @param id MongoDB _id field value to find
     *  @return model with specified _id
     */
    fun getById(id:String): Any? {
        var result = models.filter{
            val obj = it as DBModel
            obj.get("_id").toString() == id
        }
        if (result.count() == 1) {
            return result.first()
        } else {
            return null
        }
    }

    /**
     * Returns models, which meet provided condition ([field] equals [value]
     *
     * @param field Name of condition field
     * @param value value of condition field
     * @return array of matched items or null if no items found
     */
    fun getListBy(field:String,value:Any): ArrayList<Any>? {
        val result = models.filter {
            val model = it as DBModel
            it[field] == value
        }
        if (result.count() > 0) {
            return result as ArrayList<Any>
        } else {
            return null
        }
    }

    /**
     * Returs first model in collection, which meets provided criteria ([field] equals [value])
     *
     * @param field Name of condition field
     * @param value Value of condition field
     * @return matched model or null if no matches
     */
    fun getBy(field:String,value:Any): DBModel? {
        val result = getListBy(field,value)
        if (result == null) {
            return null
        } else {
            return result.first() as DBModel
        }
    }

    /**
     * Remove model with specified ID
     * @param id MongoDB _id field of model to remove
     * @param callback callback function after operation completed
     */
    fun remove(id:String,callback:(status:Boolean)->Unit) {
        val obj = getById(id)
        if (obj == null) {
            callback(false)
        } else {
            val model = obj as DBModel
            model.remove {
                models.remove(model)
                callback(true)
            }
        }
    }

    /**
     * Returns string representation of collection
     * @return String representation of collection (JSON with _id's)
     */
    override fun toString() : String {
        var it = models.iterator()
        val result = JSONArray()
        for (obj in it) {
            val model = obj as DBModel
            result.add(model["_id"])
        }
        return result.toString()
    }
}