package models

import com.mongodb.client.MongoDatabase
import javafx.scene.shape.Path
import kotlinx.coroutines.experimental.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.Adler32

/**
 * Model, which represents user in chat
 *
 * @param db link to MongoDB database instance, which contains this model
 * @param colName name of collection in MongoDB database, which contains this model
 *
 * @property schema Schema of user model used to control which fields can exist in database
 */
class User(db:MongoDatabase,colName:String) : DBModel(db,colName) {

    /**
     * Link to database schema from collection
     */
    override var schema = app.users.schema

    /**
     * Function returns user account activation status: true or false
     *
     * @return Boolean with user account activation status
     */
    fun isActive(): Boolean {
        val obj = this["active"]
        if (obj != null) {
            return obj as Boolean
        } else {
            return false
        }
    }

    /**
     * Function updates user profile image
     *
     * @param data Binary data of profile image
     * @return callback with checksum of image or null if no data or data not written
     */
    @Throws(IOException::class)
    fun setProfileImage(data: ByteArray?,callback:(checksum:Long?) -> Unit) {
        if (data != null) {
            Files.createDirectories(Paths.get(app.usersPath + "/" + this["_id"].toString()))
            var fs: FileOutputStream = FileOutputStream(app.usersPath + "/" + this["_id"].toString() + "/profile.png", false)
            fs.write(data)
            fs.close()
            val checksumEngine = Adler32()
            checksumEngine.update(data)
            callback(checksumEngine.value)
        } else {
            callback(null)
        }
    }

    /**
     * Function returns raw binary image of user profile image, if exists, or null
     *
     * @return callback with ByteArray of image data
     */
    @Throws(IOException::class)
    fun getProfileImage(callback:(byteArray:ByteArray?) -> Unit) {
        launch {
            if (Files.exists(Paths.get(app.usersPath + "/" + this@User["_id"].toString() + "/profile.png"))) {
                val fs = FileInputStream("opt/chatter/users/" + this@User["_id"] + "/profile.png")
                callback(fs.readBytes())
            } else {
                callback(null)
            }
        }
    }

    /**
     * Function returns path to user profile image, relative to appliation root path if image exists
     * Otherwise returns null
     *
     * @return callback with path as String
     */
    fun getProfileImagePath(callback:(path:String?)->Unit) {
        val path = app.usersPath+"/"+this["_id"].toString()+"/profile.png"
        if (Files.exists(Paths.get(path))) {
            callback(path)
        } else {
            callback(null)
        }
    }

}