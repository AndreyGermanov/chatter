/**
 * Created by andrey on 2/20/18.
 */
package interactors

import com.mongodb.client.MongoDatabase

class Rooms(db: MongoDatabase, colName:String): DBCollection(db,colName) {

}
