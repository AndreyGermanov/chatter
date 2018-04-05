package core
import com.mongodb.MongoClient
import com.mongodb.client.MongoDatabase

/**
 * MongoDB database wrapper class. Holds MongoDB database instance
 *
 * @param db Main MongoDB database instance
 * @property db Main MongoDB database instance
 *
 * @param db_host: Host name of database server
 * @param db_name: Database name
 */
public class DB(db_name:String = "chatter",db_host:String="localhost") {
    var db: MongoDatabase = MongoClient(db_host).getDatabase(db_name)

}