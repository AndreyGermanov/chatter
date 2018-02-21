package core
import com.mongodb.MongoClient
import com.mongodb.client.MongoDatabase

/**
 * MongoDB database wrapper class. Holds MongoDB database instance
 *
 * @param db Main MongoDB database instance
 * @property db Main MongoDB database instance
 */
public class DB(db_name:String = "chatter") {

    var db: MongoDatabase

    init {
        this.db = MongoClient().getDatabase(db_name)
    }
}