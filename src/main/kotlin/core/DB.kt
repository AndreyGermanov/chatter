package core
import com.mongodb.MongoClient
import com.mongodb.client.MongoDatabase


public class DB {

    var db: MongoDatabase

    init {
        this.db = MongoClient().getDatabase("chatter")
    }
}