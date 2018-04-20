/**
 * Created by Andrey Germanov on 4/20/18
 */
package models

import com.mongodb.client.MongoDatabase

/**
 * Chat message model
 *
 * @param db MongoDB database instance which contains this model
 * @param colName Name of MongoDB collection of these models
 */
class Message(db: MongoDatabase, colName:String): DBModel(db,colName) {

}

