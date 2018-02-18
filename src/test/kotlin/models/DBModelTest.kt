package models

import org.junit.Before
import org.junit.Test
import core.ChatApplication
import core.DB
import interactors.DBCollection
import org.bson.Document
import org.bson.types.ObjectId
import org.json.simple.JSONObject
import org.junit.After

import org.junit.Assert.*

/**
 * Created by andrey on 2/17/18.
 */
class DBModelTest {
    lateinit var model:DBModel

    @Before
    fun setUp() {
        ChatApplication.dBServer = DB()
        model = DBModel(ChatApplication.dBServer.db,"test")
        model.schema = mapOf("_id" to "Any", "intField" to "Int", "boolField" to "Boolean", "stringField" to "String") as HashMap<String,String>
        model["_id"] = ObjectId.get()
        model["intField"] = 3
        model["boolField"] = true
        model["stringField"] = "Testing"
    }

    @Test
    fun get() {
        assertEquals("Get integer field",3,model["intField"])
        assertEquals("Get boolean field",true,model["boolField"])
        assertEquals("Get string Field","Testing",model["stringField"])
        assertEquals("Get non exist field",null,model["unknown"])
    }

    @Test
    fun set() {
        model["unknown"] = 10
        model["intField"] = 5
        model["boolField"] = false
        model["stringField"] = "New value"
        assertEquals("Set field which is not in schema",null,model["unknown"])
        assertEquals("Set integer field",5,model["intField"])
        assertEquals("Set boolean field", false,model["boolField"])
        assertEquals("Set string field", "New value",model["stringField"])
        assertEquals("Set non exist field",null,model["unset"])
    }

    @Test
    fun save() {
        model.save() {
            model["intField"] = 5
            model["boolField"] = false
            model["stringField"] = "New value"
            assertEquals("Insert field which is not in schema",null,model["unknown"])
            assertEquals("Insert integer field",5,model["intField"])
            assertEquals("Insert boolean field", false,model["boolField"])
            assertEquals("Insert string field", "New value",model["stringField"])
            assertEquals("Insert non exist field",null,model["unset"])
            model["intField"] = 3
            model["boolField"] = true
            model["stringField"] = "Testing"
            model.save() {
                assertEquals("Update integer field",3,model["intField"])
                assertEquals("Update boolean field",true,model["boolField"])
                assertEquals("Update string Field","Testing",model["stringField"])
                assertEquals("Update non exist field",null,model["unknown"])
            }
        }

    }

    @Test
    fun tload() {
        var id = model["_id"]!!
        model["_id"] = "12345"
        model.load() { result ->
            assertEquals("Testing load not exist model",false,result)
            model["_id"] = id
            model["intField"] = 5
            model["boolField"] = false
            model["stringField"] = "New value"
            model.save() {
                model.load() { result ->
                    assertEquals("Load exist model", true, result)
                    assertEquals("Load integer field", 5, model["intField"])
                    assertEquals("Load boolean field", false, model["boolField"])
                    assertEquals("Load string Field", "New value", model["stringField"])
                    assertEquals("Load non exist field", null, model["unknown"])
                }
            }
        }
    }

    @Test
    fun addFromJSON() {
        var dbcol = DBCollection(model.db,model.collectionName)
        val obj = Document()
        obj.set("_id",model["_id"])
        obj.set("intField",15)
        obj.set("stringField","Str")
        obj.set("boolField",false)
        obj.set("unknown",13)
        model.addFromJSON(obj,dbcol)
        assertEquals("Add string from JSON","Str",model["stringField"])
        assertEquals("Add Integer from JSON",15,model["intField"])
        assertEquals("Add Boolean from JSON",false,model["boolField"])
        assertEquals("Add not specified in schemafield from JSON",null,model["unknown"])
        assertEquals("Getting added by JSON model from collection",1,dbcol.count())
        val newModel = dbcol.getByIndex(0)!! as DBModel
        assertEquals("Getting added by JSON model string from JSON","Str",model["stringField"])
        assertEquals("Getting added by JSON model Integer from JSON",15,model["intField"])
        assertEquals("Getting added by JSON model Boolean from JSON",false,model["boolField"])
        newModel.addFromJSON(obj,dbcol)
        assertEquals("Duplicate item in collection",1,dbcol.count())
    }

    @After
    fun unset() {
        ChatApplication.dBServer.db.getCollection("test").deleteOne(Document("_id",model["_id"]))
    }

}