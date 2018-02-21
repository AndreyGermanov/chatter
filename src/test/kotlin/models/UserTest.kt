package models

import com.mongodb.client.MongoDatabase
import core.ChatApplication
import core.DB
import interactors.Users
import javafx.scene.shape.Path
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.After
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.internal.runners.statements.ExpectException
import org.junit.rules.ExpectedException
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.Adler32

/**
 * Created by andrey on 2/21/18.
 */
class UserTest {

    lateinit var db:MongoDatabase
    lateinit var user:User
    val colName = "test"
    val dbName = "test"
    val app = ChatApplication

    @get:Rule
    public val thrown = ExpectedException.none()

    @Before
    fun setUp() {
        ChatApplication.dBServer = DB(dbName)
        db = ChatApplication.dBServer.db
        app.usersPath = app.rootPath+"/users"
        app.users = Users(db,colName)
    }

    @After
    fun tearDown() {
        val user = getMockUser()
        db.getCollection(colName).deleteMany(Document())
        user.getProfileImagePath { path ->
            if (path!=null) {
                Files.deleteIfExists(Paths.get(path))
                Files.deleteIfExists(Paths.get(path).parent)
            }
        }
    }

    fun getMockUser() : User {
        val user = User(db,colName)
        user["_id"] = ObjectId.get()
        user["login"] = "andrey"
        user["password"] = "12345"
        return user
    }

    @Test
    fun isActive() {
        user = getMockUser()
        user.save {
            app.users.addModel(user)
            val found_user = app.users.getBy("login","andrey") as User
            assertEquals("Check user is active flag of not exist",false,found_user.isActive())
            user["active"] = true
            assertEquals("Check user is active flag",true,found_user.isActive())
        }
    }

    @Test
    fun getProfileImagePath() {
        var user = getMockUser()
        user.getProfileImagePath { path ->
            assertEquals("Check path of user profile image, if it does not exist",null,path)
            val stream = FileInputStream("src/test/resources/profile.png")
            val data = stream.readBytes()
            val checksumEngine = Adler32()
            checksumEngine.update(data)
            val origChecksum = checksumEngine.value
            user.setProfileImage(data) {
                user.getProfileImagePath { path ->
                    val readStream = FileInputStream(path)
                    var readData = readStream.readBytes()
                    checksumEngine.reset()
                    checksumEngine.update(readData)
                    assertEquals("Test that it returns path to correct profile image file",origChecksum,checksumEngine.value)
                }
            }
        }
    }

    @Test
    fun setProfileImage() {
        val user = getMockUser()
        val stream = FileInputStream("src/test/resources/profile.png")
        val data = stream.readBytes()
        val checksumEngine = Adler32()
        checksumEngine.update(data)
        val origChecksum = checksumEngine.value
        user.setProfileImage(data) { checksum ->
            assertEquals("Test checksum of written user profile image ",origChecksum,checksum)
            user.getProfileImagePath { path ->
                val stream = FileInputStream(path)
                val readData = stream.readBytes()
                checksumEngine.reset()
                checksumEngine.update(readData)
                assertEquals("Test correct placement of user profile image",origChecksum,checksumEngine.value)
                app.usersPath = "/usr/sbin/"
                try {
                    user.setProfileImage(data) {
                        fail("Expected IOException should be thrown")
                    }
                } catch (e:Exception) {
                }
            }
        }
    }

    @Test
    fun getProfileImage() {
        user = getMockUser()
        user.getProfileImage { img ->
            assertEquals("Check not exist profile image", null, img)
            val stream = FileInputStream(app.rootPath + "/src/test/resources/profile.png")
            val data = stream.readBytes()
            user.setProfileImage(data) { checksum ->
                user.getProfileImage { data ->
                    val checksumEngine = Adler32()
                    checksumEngine.update(data)
                    assertEquals("Check existing profile image",checksum,checksumEngine.value)
                    val origPath = app.usersPath
                    app.usersPath = "/usr/sbin/"
                    try {
                        user.setProfileImage(data) {}

                    } catch (e:Exception) {
                        fail("Expected IOException should be thrown")
                    }
                }
            }
        }
    }

}