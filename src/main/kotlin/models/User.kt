package models

import com.mongodb.client.MongoDatabase

/**
 * Model, which represents user in chat
 *
 * @param db link to MongoDB database instance, which contains this model
 * @param colName name of collection in MongoDB database, which contains this model
 */
class User(db:MongoDatabase,colName:String) : DBModel(db,colName) {
    override var schema = mapOf(
            "_id" to "String",
            "login" to "String",
            "password" to "String",
            "email" to "String",
            "default_room" to "String",
            "active" to "Boolean",
            "first_name" to "String",
            "last_name" to "String",
            "gender" to "String",
            "birthDate" to "Int"
    ) as HashMap<String,String>
}