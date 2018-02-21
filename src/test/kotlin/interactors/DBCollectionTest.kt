package interactors

import com.mongodb.client.MongoDatabase
import core.ChatApplication
import core.DB
import models.DBModel
import org.bson.Document
import org.junit.After
import org.junit.Before
import org.junit.Test

import org.junit.Assert.*

/**
 * Created by andrey on 2/18/18.
 */
class DBCollectionTest {
    lateinit var dbcol: DBCollection
    lateinit var db:MongoDatabase
    lateinit var model:DBModel
    lateinit var schema:HashMap<String,String>
    val colName = "test"
    val dbName = "test"

    @Before
    fun setUp() {
        ChatApplication.dBServer = DB(dbName)
        db = ChatApplication.dBServer.db
        dbcol = DBCollection(ChatApplication.dBServer.db,colName)
        model = DBModel(db,colName)
        schema = mapOf("_id" to "Any", "intField" to "Int", "boolField" to "Boolean", "stringField" to "String") as HashMap<String,String>
        model.schema = schema
        dbcol.schema = model.schema
    }

    @After
    fun tearDown() {
        db.getCollection(colName).deleteMany(Document())
    }


    fun addDemoData(callback:()->Unit) {
        model["_id"] = "123456"
        model["stringField"] = "Test 1"
        model["boolField"] = true
        model["intField"] = 35
        model.save {
            model["_id"] = "987654321"
            model["stringField"] = "Test 2"
            model["boolField"] = false
            model["intField"] = 16
            model.save {
                model["_id"] = "new_id"
                model["stringField"] = "Test 3"
                model["boolField"] = true
                model["intField"] = 135
                model.save {
                    callback()
                }
            }
        }
    }

    @Test
    fun addItem() {
        val doc = Document()
        doc.set("stringField","test 5")
        doc.set("boolField", false)
        doc.set("intField",145)
        dbcol.addItem(doc,model.schema)
        assertEquals("Add item to collection",1,dbcol.count())
        doc["_id"] = "12345"
        dbcol.addItem(doc,model.schema)
        assertEquals("Add duplicate item to collection",2,dbcol.count())
        dbcol.addItem(doc,model.schema)
        assertEquals("Add duplicate item to collection",2,dbcol.count())
    }

    @Test
    fun addModel() {
        assertEquals("Before add new model to collection",0,dbcol.count())
        val dbmodel = DBModel(db,colName)
        dbmodel.schema = schema
        dbmodel["_id"] = "78901234"
        dbmodel["boolValue"] = false
        dbmodel["stringValue"] = "Test"
        dbmodel["intValue"] = 15
        dbcol.addModel(dbmodel)
        assertEquals("Add new model to collection",1,dbcol.count())
        dbcol.addModel(dbmodel)
        assertEquals("Add duplicate model to collection",1,dbcol.count())
    }

    @Test
    fun loadList() {
        val cond = Document()
        addDemoData {
            cond.set("intField",Document("\$gt",16))
            dbcol.loadList(cond) {
                assertEquals("Load filtered models list from collection",2,dbcol.count())
                dbcol.loadList(null) {
                    assertEquals("Load all models from collection",3,dbcol.count())

                }
            }
        }
    }

    @Test
    fun getList() {
        addDemoData {
            dbcol.loadList(null) {
                assertEquals("Number of items in collection", 3, dbcol.getList().count())
            }
        }
    }

    @Test
    operator fun next() {
        addDemoData {
            dbcol.loadList(null) {
                var it = dbcol.next() as DBModel
                assertEquals("Iterator test 1", "123456", it["_id"].toString())
                it = dbcol.next() as DBModel
                assertEquals("Iterator test 2", "987654321", it["_id"].toString())
                it = dbcol.next() as DBModel
                assertEquals("Iterator test 3", "new_id", it["_id"].toString())
                try {
                    val out = dbcol.next()
                    org.junit.Assert.assertEquals("Iterator overflow test", null,out)
                } catch (e:Exception) {
                    org.junit.Assert.assertEquals("Iterator overflow test", null,null)
                }
            }
        }
    }

    @Test
    fun hasNext() {
        addDemoData {
            dbcol.loadList(null) {
                assertEquals("Has next Iterator test 1", true, dbcol.hasNext())
                dbcol.next()
                assertEquals("Has next Iterator test 2", true, dbcol.hasNext())
                dbcol.next()
                assertEquals("Has next Iterator test 3", true, dbcol.hasNext())
                dbcol.next()
                assertEquals("Has next Iterator test 4", false, dbcol.hasNext())
            }
        }
    }

    @Test
    fun count() {
        addDemoData {
            dbcol.loadList(null) {
                assertEquals("Collection models count",3,dbcol.count())
            }
        }
    }

    @Test
    fun getByIndex() {
        addDemoData {
            dbcol.loadList(null) {
                val item = dbcol.getByIndex(1) as DBModel
                assertEquals("Get item by index","987654321",item["_id"])
            }
        }
    }

    @Test
    fun getById() {
        addDemoData {
            dbcol.loadList(null) {
                val item = dbcol.getById("987654321") as DBModel
                assertNotEquals("Get item by id",null,item)
            }
        }
    }

    @Test
    fun getListBy() {
        addDemoData {
            dbcol.loadList(null) {
                var items = dbcol.getListBy("boolField",true)
                assertEquals("Get model list by condition",2,items!!.count())
                items = dbcol.getListBy("fdfgdw",222)
                assertEquals("Get model list by condition",null,items)
            }
        }
    }

    @Test
    fun getBy() {
        addDemoData {
            dbcol.loadList(null) {
                var item = dbcol.getBy("stringField","Test 3") as DBModel?
                assertEquals("Get model by condition","new_id",item!!["_id"])
                item = dbcol.getBy("_id","123456") as DBModel?
                assertEquals("Get model by condition",item!!["intField"],35)
                item = dbcol.getBy("gd33r","3sdg") as DBModel?
                assertEquals("Get unknown model by id",null,item)
            }
        }
    }

    @Test
    fun remove() {
        addDemoData {
            dbcol.loadList(null) {
                dbcol.remove("987654321") {
                    val col = db.getCollection(colName)
                    assertEquals("Remove model from MongoDB database",0,col.find(Document("_id","987654321")).count())
                    assertEquals("Remove model from collection",null, dbcol.getById("987654321"))
                }
            }
        }
    }

}