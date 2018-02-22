/**
 * Created by andrey on 2/20/18.
 */
package models

import com.mongodb.client.MongoDatabase
import core.ChatApplication
import interactors.DBCollection

/**
 * Model which represents chat room in database
 *
 * @param db MongoDB database instance which contains this model
 * @param colName Name of MongoDB collection of these models
 *
 * @property schema Schema of room model which controls which fields can exist in database
 */
class Room(db: MongoDatabase, colName:String): DBModel(db,colName) {

    /**
     * Link to database schema from collection
     */
    override var schema = app.rooms.schema

    /**
     * Function returns list of user sessions, which currently in this room
     *
     * @return ArrayList of users in this room
     */
    fun getUserSessions(): ArrayList<Any>? {
        return ChatApplication.sessions.getListBy("room",this["_id"].toString())
    }
}
