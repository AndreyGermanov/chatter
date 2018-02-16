package core

import com.mongodb.Block
import com.mongodb.client.MongoCursor
import com.mongodb.client.model.Filters.*
import kotlinx.coroutines.experimental.*

import models.User
import org.bson.Document
import org.bson.conversions.Bson
import org.json.simple.JSONObject
import java.util.*
import utils.BCrypt
import utils.SendMail
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths

object Users {

    private var _users: ArrayList<User> = ArrayList<User>()
    lateinit var application: ChatApplication

    public fun loadUsers(callback:(ArrayList<User>)->Unit) {
        var col = this.application.dBServer.db.getCollection("users")
        _users.clear()
        launch {
            val cursor: MongoCursor<Document> = col.find().iterator()
            while (cursor.hasNext()) {
                val doc = cursor.next()
                var user = User(
                        login = doc["login"].toString(),
                        password = doc["password"].toString(),
                        email = doc["email"].toString(),
                        id = doc["id"].toString())
                _users.add(user)
            }
            callback(_users)
        }
    }

    public fun getUsers() : ArrayList<User> {
        return _users
    }

    public fun registerUser(params: JSONObject,callback: (HashMap<String,String>) -> Unit ) {
        var col = this.application.dBServer.db.getCollection("users")
        var result = HashMap<String,String>()
        launch {
            if (col.find(or(eq("login",params.get("login")),eq("email",params.get("email")))).count()>0) {
                result.set("request_id",params.get("request_id").toString())
                result.set("status","error")
                result.set("message","User with provided login already exists");
                callback(result)
            } else {
                val doc = Document()
                var id:String = BCrypt.hashpw(params.get("password").toString()+params.get("email").toString(),BCrypt.gensalt(12))
                doc.append("login",params.get("login"))
                doc.append("password",BCrypt.hashpw(params.get("password").toString(),BCrypt.gensalt(12)))
                doc.append("email",params.get("email"))
                doc.append("_id",id)
                doc.append("active",false)
                val smtpClient = SendMail(params.get("email").toString(),"Chatter Account Activation")
                smtpClient.sendMessage("Please, follow this link to activate your Chatter account http://192.168.0.184:8080/activate/"+id)
                col.insertOne(doc)
                result.set("request_id",params.get("request_id").toString())
                result.set("status","ok")
                result.set("message","Check email to activate your account")
                callback(result)
            }
        }
    }

    public fun activateUser(token:String) {
        val col = this.application.dBServer.db.getCollection("users")
        if (col.find(eq("_id",token)).count()>0) {
            col.updateOne(eq("_id",token),Document("\$set",Document("active",true)))
        }
    }

    public fun loginUser(params: JSONObject,callback: (HashMap<String,String>) -> Unit ) {
        var col = this.application.dBServer.db.getCollection("users")
        var result = HashMap<String, String>()
        launch {
            val cursor = col.find(eq("login", params.get("login")))
            result.put("action","login_user")
            if (cursor.count() > 0) {
                val doc = cursor.first()
                if (!doc.getBoolean("active")) {
                    result.put("status","error")
                    result.put("message","Pease, activate this account")
                    callback(result)
                } else if (!BCrypt.checkpw(params.get("password").toString(),doc.get("password").toString())) {
                    result.put("status","error")
                    result.put("message","Incorrect password")
                    callback(result)
                } else {
                    result.put("user_id", doc.getString("_id"))
                    if (doc.containsKey("first_name")) {
                        result.put("first_name",doc.getString("first_name"))
                    }
                    if (doc.containsKey("last_name")) {
                        result.put("last_name",doc.getString("last_name"))
                    }
                    if (doc.containsKey("gender")) {
                        result.put("gender",doc.getString("gender"))
                    }
                    if (doc.containsKey("birthDate")) {
                        result.put("birthDate",doc.getInteger("birthDate").toString())
                    }
                    if (doc.containsKey("default_room")) {
                        result.put("default_room", doc.getString("default_room"))
                    }
                    result.put("status", "ok")
                    if (findUserById(doc.getString("_id")) == null) {
                        val user = User(login = doc.getString("login"), email = doc.getString("email"), id = doc.getString("_id"), password = doc.getString("password"))
                        _users.add(user)
                    }
                    callback(result)
                }
            } else {
                result.put("status","error")
                result.put("message","User not found")
                callback(result)
            }
        }
    }

    public fun findUserById(id:String): User? {
        val results = _users.filter {
            it.id == id
        }
        if (results.count()>0) {
            return results.first()
        } else {
            return null
        }
    }

    public fun updateUser(params: JSONObject,callback: (HashMap<String,String>) -> Unit ) {
        var col = this.application.dBServer.db.getCollection("users")
        var result = HashMap<String, String>()
        result.put("action","update_user_profile")
        result.put("user_id",params.get("user_id").toString())
        launch {
            if (col.find(eq("_id", params.get("user_id"))).count() > 0) {
                val doc = Document()
                if (params.containsKey("first_name")) {
                    doc.append("first_name", params.get("first_name"))
                }
                if (params.containsKey("last_name")) {
                    doc.append("last_name",params.get("last_name"))
                }
                if (params.containsKey("gender")) {
                    doc.append("gender",params.get("gender"))
                }
                if (params.containsKey("birthDate")) {
                    doc.append("birthDate",Integer.parseInt(params.get("birthDate").toString()))
                }
                if (params.containsKey("default_room")) {
                    doc.append("default_room",params.get("default_room"))
                }
                col.updateOne(eq("_id",params.get("user_id")),Document("\$set",doc))
                result.put("status","ok")
            } else {
                result.put("status","error")
                result.put("message","User not found")
            }
            callback(result)
        }
    }

    public fun getUserProfileImage(user_id:String,callback:(byteArray:ByteArray?) -> Unit) {
        launch {
            if (Files.exists(Paths.get("opt/chatter/users/" + user_id + "/profile.png"))) {
                val fs = FileInputStream("opt/chatter/users/" + user_id + "/profile.png")
                callback(fs.readBytes())
            } else {
                callback(null)
            }
        }
    }
}