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
 *   Base class of collection of database models
 *
 *  @param db MongoDB database instance, which holds collection
 *  @param colName Name of MongoDB collection
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
    protected var models: ArrayList<Any> = ArrayList()
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
        model.schema = this.schema
        if (schema != null) {
            model.schema = schema
        }
        model.addFromJSON(doc,this)
    }

    /**
    *   Internal method used to add model to collection from external classes
    *
    *   @param model Model to add
    */
    open fun addModel(model:Any) {
        val dbmodel = model as DBModel
        val items = models.filter {
            val item = it as DBModel
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
        val result: FindIterable<Document>
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
     *          get_total: if specified, then result will include total number of items in collection
     *          after applying filter (but before applying offset and limit). Total value will be returned as last
     *          item of array
     *          get_presentations: if specified, will use field presentations if field string presentation for field
     *          differs from real database value . In this case "filter" will be applied to presentation of field,
     *          not to actual field value
     * @param condition: Condition or set of conditions in MongoDB Document format, used to set additional filter to
     * returned list. If specified, then function first applies "condition" and then applies "params" to resulting set
     * @return ArrayList with all models, which meet criteria
    */
    fun getList(params:JSONObject?=null,condition:Document? = null):ArrayList<Any> {
        var filter = ""
        var fields:ArrayList<String>? = null
        var limit = 0
        var offset = 0
        var getTotal = false
        var getPresentations = false
        var sort:Pair<String,String>? = null
        if (condition!=null) {
            this.loadList(condition) {}
        }
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
            getTotal = params["get_total"] != null
            getPresentations = params["get_presentations"] != null
        }
        val results = ArrayList<Any>()
        if (!filter.isEmpty()) {
            this.models.map { it as DBModel }.forEach {
                for ((field,_) in schema) {
                    if (it[field] != null && (fields==null || fields!!.contains(field))) {
                        if (field == "_id") {
                            continue
                        }
                        var fieldValue = it[field].toString().toLowerCase()
                        if (getPresentations) {
                            fieldValue = getFieldPresentation(it,field).toLowerCase()
                        }
                        if (fieldValue.startsWith(filter,true)) {
                            results.add(it)
                            break
                        }
                    }
                }
            }
        } else {
            results.addAll(this.models)
        }
        if (sort!=null && schema[sort.first]!=null) {
            results.sortWith(Comparator<Any> { obj1, obj2 ->
                getSortOrder(obj1,obj2,sort!!) ?: 0
            })
        }
        val count = results.size
        if (offset>0 || limit>0) {
            var endIndex = offset+limit
            if (endIndex == 0 || endIndex>results.size || limit == 0) {
                endIndex = results.size
            }
            val sublist = results.subList(offset,endIndex)
            if (sublist.count()>0) {
                val subListArray = ArrayList<Any>()
                subListArray.addAll(sublist)
                if (getTotal) {
                    subListArray.add(count)
                }
                return subListArray
            }
        }
        if (getTotal) {
            results.add(count)
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
     *          get_total: if specified, then result will include total number of items in collection
     *          after applying filter (but before applying offset and limit). Total value will be returned as last
     *          item of array
     *          get_presentations: if specified, will use field presentations if field string presentation for field
     *          differs from real database value . In this case "filter" will be applied to presentation of field,
     *          not to actual field value. Also, it will add additional fields to result in a form "<field_id>_text"
     *          for all fields, which string presentation differs from actual database value
     * @return JSONArray with all models, which meet criteria
     */
    fun getListJSON(params:JSONObject?=null):JSONArray {
        val results = JSONArray()
        val models = this.getList(params)
        var fields:ArrayList<String>? = null
        var getPresentations = false
        if (params !=null) {
            fields = params["fields"] as? ArrayList<String>
            getPresentations = params["get_presentations"] != null
        }
        for (modelObj in models) {
            val jsonObj = JSONObject()
            val model = modelObj as? DBModel
            if (model==null) {
                var count:Int
                try {
                    count = modelObj.toString().toInt()
                    results.add(count)
                } catch (e: Exception) {
                    Logger.log(LogLevel.WARNING,"Could not parse row from returned users list: $modelObj",
                            "DBCollection","getListJSON")
                }
                continue
            }
            for ((field_index,_) in schema) {
                if (model[field_index]!=null && (fields==null || fields.contains(field_index)) && field_index!="password") {
                    val field_presentation = getFieldPresentation(model,field_index)
                    jsonObj[field_index] = model[field_index]
                    if (getPresentations && field_presentation != model[field_index].toString())  {
                        jsonObj[field_index+"_text"] = field_presentation
                    }
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
     * Function calculates string representation of field with provided [field_id]
     * of provided [obj]
     *
     * @param obj: Model from which need to get field
     * @param field_id: ID of field which need to extract and get representation
     * @return Human readable string representation of field value
     */
    open fun getFieldPresentation(obj: Any,field_id:String):String {
        val model = obj as? DBModel
        if (model == null) {
            Logger.log(LogLevel.WARNING, "Could not convert object to model to get text representation " +
                    "for field '$field_id'", "DBCollection","getFieldPresentation")
            return ""
        }
        if (model[field_id]==null) {
            Logger.log(LogLevel.WARNING, "Field with ID '$field_id' does not exist in $model",
                    "DBCollection","getFieldPresentation")
            return ""
        }
        return model[field_id].toString()
    }

    /**
     * Function calculates sort order for models [obj1] and [obj2] when sorting using [sort_order] param
     *
     * @param obj1: First model
     * @param obj2: Second model
     * @param sortOrder: Pair<String,String> which contains sorting rule. First part is field_id,second part is
     * sort direction: ASC or DESC
     * @returns When sort order is ASC, returns 1 if obj1>obj2, -1 if obj1<obj2 and 0 if they are equals
     *          When sort order is DESC, returns 1 if obj1<obj2, -1 if obj1>obj2 and 0 if they are equals
     *          Else returns null in case of error during operation
     */
    open fun getSortOrder(obj1:Any, obj2:Any,sortOrder:Pair<String,String>): Int? {
        val model1 = obj1 as? DBModel
        val model2 = obj2 as? DBModel
        if (model1 == null) {
            Logger.log(LogLevel.WARNING,"Object 1 ($obj1) is not correct database model",
                    "DBCollection","getSortOrder")
            return null
        }
        if (model2 == null) {
            Logger.log(LogLevel.WARNING,"Object 2 ($obj2) is not correct database model",
                    "DBCollection","getSortOrder")
            return null
        }
        val sortField = sortOrder.first
        val sortType = schema[sortOrder.first].toString()
        var corrector = 1
        if (sortOrder.second == "DESC") {
            corrector = -1
        }
        var result:Int
        when (sortType) {
            "Double" -> {
                var v1 = 0.0
                var v2 = 0.0
                try {
                    v1 = model1[sortField]?.toString()?.toDouble() ?: 0.0
                    v2 = model2[sortField]?.toString()?.toDouble() ?: 0.0
                } catch (e:Exception) {
                    Logger.log(LogLevel.WARNING,"Could not convert values of field $sortField to Double " +
                            "for sorting: ${model1[sortField]},${model2[sortField]}","DBCollection","getSortOrder")
                }
                if (v1>v2) {
                    result = 1*corrector
                } else if (v1<v2) {
                    result = -1*corrector
                } else {
                    result = 0
                }
            }
            "Int" -> {
                var v1 = 0
                var v2 = 0
                try {
                    v1 = model1[sortField]?.toString()?.toInt() ?: 0
                    v2 = model2[sortField]?.toString()?.toInt() ?: 0
                } catch (e:Exception) {
                    Logger.log(LogLevel.WARNING,"Could not convert values of field $sortField to Int " +
                            "for sorting: ${model1[sortField]},${model2[sortField]}","DBCollection","getList")
                }
                if (v1>v2) {
                    result = 1*corrector
                } else if (v1<v2) {
                    result = -1*corrector
                } else {
                    result = 0
                }
            }
            "Boolean" -> {
                var v1 = false
                var v2 = false
                try {
                    v1 = model1[sortField].toString().toBoolean()
                    v2 = model2[sortField].toString().toBoolean()
                } catch (e:Exception) {
                    Logger.log(LogLevel.WARNING,"Could not convert values of field $sortField to Boolean " +
                            "for sorting: ${model1[sortField]},${model2[sortField]}","DBCollection","getList")
                }
                if (v1>v2) {
                    result = 1*corrector
                } else if (v1<v2) {
                    result = -1*corrector
                } else {
                    result = 0
                }
            }
            else -> {
                val v1 = model1[sortField]?.toString()?.toLowerCase() ?: ""
                val v2 = model2[sortField]?.toString()?.toLowerCase() ?: ""
                if (v1>v2) {
                    result =1*corrector
                } else if (v1<v2) {
                    result = -1*corrector
                } else {
                    result = 0
                }
            }
        }
        return result
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
        val result = models.filter{
            val obj = it as DBModel
            obj.get("_id").toString() == id
        }
        return if (result.count() == 1) {
            result.first()
        } else {
            null
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
            model[field] == value
        }
        return if (result.count() > 0) {
            result as ArrayList<Any>
        } else {
            null
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
        Logger.log(LogLevel.DEBUG,"Begin removing object $id from collection.","DBCollection","remove")
        if (obj == null) {
            callback(false)
            Logger.log(LogLevel.DEBUG,"Object with $id not found in collection.","DBCollection","remove")
        } else {
            val model = obj as DBModel
            Logger.log(LogLevel.DEBUG,"Removing $id from MongoDB Database.","DBCollection","remove")
            model.remove {
                Logger.log(LogLevel.DEBUG,"Removing $model from collection.","DBCollection","remove")
                if (models.remove(model)) {
                    Logger.log(LogLevel.DEBUG,"Removed $model from collection.","DBCollection","remove")
                } else {
                    Logger.log(LogLevel.DEBUG,"Failed to remove $model from collection.","DBCollection","remove")
                }
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
        val it = models.iterator()
        val result = JSONArray()
        for (obj in it) {
            val model = obj as DBModel
            result.add(model["_id"])
        }
        return result.toString()
    }
}