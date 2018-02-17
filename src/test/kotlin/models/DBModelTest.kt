package models

import org.junit.Before
import org.junit.Test
import core.ChatApplication
import core.DB
import org.bson.Document
import org.bson.types.ObjectId
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

    @After
    fun unset() {
        ChatApplication.dBServer.db.getCollection("test").deleteOne(Document("_id",model["_id"]))
    }

}