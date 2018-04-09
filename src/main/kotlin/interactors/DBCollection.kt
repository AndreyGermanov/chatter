/**
 * Created by Andrey Germanov on 2/17/18.
 */
package interactors

import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCursor
import com.mongodb.client.MongoDatabase
import com.mongodb.util.JSON
import core.ChatApplication
import kotlinx.coroutines.experimental.launch
import models.DBModel
import models.User
import org.bson.Document
import org.bson.types.ObjectId
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import utils.LogLevel
import utils.Logger

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
    var app = ChatApplication

    open var schema = hashMapOf("_id" to "String")

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
        } else if (this.schema != null) {
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
            if (model["_id"]==null) {
                model["_id"] = ObjectId.get()
            }
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
     * @param params: Optional params, used to filter and format resulting list. Can include:
     *          fields: Fields to use for filtering. If no fields specified, all fields used for filtering
     *          filter: string, applied to fields. Returned only records, in which any field begins with "filter"
     *          offset: starting record (for pagination). If not specified then starts from begining
     *          limit: number of records to return . If not specified, all records returned
     *          sort: field used for sorting. It is a pair of "field" to (DESC or ASC).
     *          If not specified, natural sorting used, as loaded from database
     * @return ArrayList with all models, which meet criteria
    */
    fun getList(params:JSONObject?=null):ArrayList<Any> {
        var filter = ""
        var fields:ArrayList<String>? = null
        var limit = 0
        var offset = 0
        var sort:Pair<String,String>? = null
        if (params!=null) {
            filter = params["filter"]?.toString() ?: ""
            if (params["fields"] is ArrayList<*>) {
                fields = params["fields"] as ArrayList<String>
            }
            limit = params["limit"]?.toString()?.toInt() ?: 0
            offset = params["offset"]?.toString()?.toInt() ?: 0
            if (params["sort"] is Pair<*,*>) {
                sort = params["sort"] as Pair<String, String>
            }
        }
        var results = ArrayList<Any>()
        if (!filter.isEmpty()) {
            this.models.map { it as DBModel }.forEach {
                for ((field,_) in schema) {
                    if (it[field] != null && (fields==null || fields!!.contains(field))) {
                        if (it[field].toString().toLowerCase().startsWith(filter,true)) {
                            results.add(it)
                        }
                    }
                }
            }
        } else {
            results.addAll(this.models)
        }
        if (sort!=null && schema[sort.first]!=null) {
            var sortField = sort.first
            var sortType = schema[sort.first].toString()
            var corrector = 1
            if (sort.second == "DESC") {
                corrector = -1
            }
            results.sortWith(Comparator<Any> { p1, p2 ->
                val p1 = p1 as DBModel
                val p2 = p2 as DBModel
                when (sortType) {
                    "Double" -> {
                        var v1 = 0.0
                        var v2 = 0.0
                        try {
                            v1 = p1[sortField]?.toString()?.toDouble() ?: 0.0
                            v2 = p2[sortField]?.toString()?.toDouble() ?: 0.0
                        } catch (e:Exception) {
                            Logger.log(LogLevel.WARNING,"Could not convert values of field $sortField to Double " +
                                    "for sorting: ${p1[sortField]},${p2[sortField]}","DBCollection","getList")
                        }
                        if (v1>v2) {
                            1*corrector
                        } else if (v1<v2) {
                            -1*corrector
                        } else {
                            0
                        }
                    }
                    "Int" -> {
                        var v1 = 0
                        var v2 = 0
                        try {
                            v1 = p1[sortField]?.toString()?.toInt() ?: 0
                            v2 = p2[sortField]?.toString()?.toInt() ?: 0
                        } catch (e:Exception) {
                            Logger.log(LogLevel.WARNING,"Could not convert values of field $sortField to Int " +
                                    "for sorting: ${p1[sortField]},${p2[sortField]}","DBCollection","getList")
                        }
                        if (v1>v2) {
                            1*corrector
                        } else if (v1<v2) {
                            -1*corrector
                        } else {
                            0
                        }
                    }
                    "Boolean" -> {
                        var v1 = false
                        var v2 = false
                        try {
                            v1 = p1[sortField].toString()?.toBoolean() ?: false
                            v2 = p2[sortField].toString()?.toBoolean() ?: false
                        } catch (e:Exception) {
                            Logger.log(LogLevel.WARNING,"Could not convert values of field $sortField to Boolean " +
                                    "for sorting: ${p1[sortField]},${p2[sortField]}","DBCollection","getList")
                        }
                        if (v1>v2) {
                            1*corrector
                        } else if (v1<v2) {
                            -1*corrector
                        } else {
                            0
                        }
                    }
                    else -> {
                        var v1 = p1[sortField]?.toString()?.toLowerCase() ?: ""
                        var v2 = p2[sortField]?.toString()?.toLowerCase() ?: ""
                        if (v1>v2) {
                            1*corrector
                        } else if (v1<v2) {
                            -1*corrector
                        } else {
                            0
                        }
                    }
                }
            })
        }
        if (offset>0 || limit>0) {
            var endIndex = offset+limit
            if (endIndex == 0 || endIndex>results.size) {
                endIndex = results.size
            }
            var sublist = results.subList(offset,endIndex)
            if (sublist.count()>0) {
                var subListArray = ArrayList<Any>()
                subListArray.addAll(sublist)
                return subListArray
            }
        }
        return results
    }

    /**
     * Returns list of all models in collection as JSONArray
     * @param params: Optional params, used to filter and format resulting list. Can include:
     *          fields: Fields to use for filtering. If no fields specified, all fields used for filtering
     *          filter: string, applied to fields. Returned only records, in which any field begins with "filter"
     *          offset: starting record (for pagination). If not specified then starts from begining
     *          limit: number of records to return . If not specified, all records returned
     *          sort: field used for sorting. It is a pair of "field" to (DESC or ASC).
     *          If not specified, natural sorting used, as loaded from database
     * @return JSONArray with all models, which meet criteria
     */
    fun getListJSON(params:JSONObject):JSONArray {
        val results = JSONArray()
        val models = this.getList(params)
        val fields = params["fields"] as? ArrayList<String>
        for (modelObj in models) {
            val jsonObj = JSONObject()
            val model = modelObj as DBModel
            for ((field_index,_) in schema) {
                if (model[field_index]!=null && (fields==null || fields.contains(field_index))) {
                    jsonObj[field_index] = model[field_index]
                }
            }
            if (jsonObj.count()>0) {
                results.add(jsonObj)
            } else {
                Logger.log(LogLevel.DEBUG,"Empty JSON for model $model, using fields: $fields","DBCollection",
                        "getListJSON")
            }
        }
        return results
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
        val result = currentItem < models.count()
        if (!result) {
            currentItem=0
        }
        return result
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
     * Removes model from memory collection hashmap, but not from MongoDB database
     * @param id ID of model to remove
     */
    fun detach(id:String) {
        val obj = getById(id)
        if (obj !=null) {
            this.models.remove(obj)
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