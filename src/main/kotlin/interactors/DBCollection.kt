package interactors

import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCursor
import com.mongodb.client.MongoDatabase
import core.ChatApplication
import kotlinx.coroutines.experimental.launch
import models.DBModel
import org.bson.Document

/**
 * Created by andrey on 2/17/18.
 */

/*
* Base class of collection of database models of type [T] based on DBModel
*
 */
class DBCollection(db:MongoDatabase,colName:String) {

    var collectionName = ""
    var db:MongoDatabase
    private val models: ArrayList<DBModel> = ArrayList<DBModel>()

    init {
        collectionName = colName
        this.db = db
    }

    fun loadList(condition: Document?, callback:()->Unit) {
        val col = db.getCollection(collectionName)
        launch {
            var result: FindIterable<Document>?
            if (condition!=null) {
                result = col.find(condition)
            } else {
                result = col.find()
            }
        }
    }
}