/**
 * Created by Andrey Germanov on 2/18/18.
 */
package interactors

import com.mongodb.client.MongoDatabase
import core.ChatApplication
import org.bson.Document
import models.User
import models.Session
import org.bson.types.ObjectId
import org.json.simple.JSONObject
import utils.BCrypt
import utils.LogLevel
import utils.Logger
import utils.SendMail
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

/**
 * Class represents iterable collection of chat users, based on MongoDB collection
 *
 * @param db Link to MongoDB database
 * @param colName Name of collection in MongoDB database
 */
class Users(db: MongoDatabase, colName:String = "users"): DBCollection(db,colName) {

    /**
     * Schema of database model
     */
    override var schema = hashMapOf(
            "_id" to "String",
            "login" to "String",
            "password" to "String",
            "email" to "String",
            "default_room" to "String",
            "active" to "Boolean",
            "first_name" to "String",
            "last_name" to "String",
            "gender" to "String",
            "birthDate" to "Int",
            "role" to "Int"
    ) as HashMap<String,String>

    val SALT_ROUNDS = 12
    val USER_ACTIVITY_TIMEOUT = 10
    val USER_LOGIN_TIMEOUT = 300

    /**
     * Used to load model from JSON document, using correct model type for this
     *
     * @param doc JSON document
     */
    override fun addItem(doc: Document, schema: HashMap<String,String>?) {
        User(db, collectionName).addFromJSON(doc,this)
    }

    /**
     * Function reginsters new user
     * @params params Object with user registration fields. Should contain 'login', 'password' and 'email'
     * @return calls callback with registration result code and registered user model, if result code is RESULT_OK
     */
    fun register(params: JSONObject, callback:(result_code:UserRegisterResultCode,user:User?) -> Unit) {
        Logger.log(LogLevel.DEBUG,"Begin register user. Params: $params.","Users","register")
        if (!params.contains("login")) {
            callback(UserRegisterResultCode.RESULT_ERROR_NO_LOGIN,null)
        } else if (params.get("login").toString().isEmpty()) {
            callback(UserRegisterResultCode.RESULT_ERROR_NO_LOGIN, null)
        } else if (!params.contains("email")) {
            callback(UserRegisterResultCode.RESULT_ERROR_NO_EMAIL,null)
        } else if (params.get("email").toString().isEmpty()) {
            callback(UserRegisterResultCode.RESULT_ERROR_NO_EMAIL, null)
        } else if (!params.contains("password")) {
            callback(UserRegisterResultCode.RESULT_ERROR_NO_PASSWORD,null)
        } else if (params.get("password").toString().isEmpty()) {
            callback(UserRegisterResultCode.RESULT_ERROR_NO_PASSWORD,null)
        } else {
            Logger.log(LogLevel.DEBUG,"Continue register user after simple empty fields validation. " +
                    "Params: $params.","Users","register")
            val col = db.getCollection(collectionName)
            if (col.find(Document("login",params.get("login").toString())).count()>0) {
                callback(UserRegisterResultCode.RESULT_ERROR_LOGIN_EXISTS,null)
            } else if (col.find(Document("email",params.get("email").toString())).count()>0) {
                callback(UserRegisterResultCode.RESULT_ERROR_EMAIL_EXISTS,null)
            } else {
                Logger.log(LogLevel.DEBUG,"Continue register user after 'user exists' validation. " +
                        "Params: $params.","Users","register")
                val user = User(db,collectionName)
                user["_id"] = ObjectId.get()
                user["login"] = params.get("login").toString()
                user["email"] = params.get("email").toString()
                user["password"] = BCrypt.hashpw(params.get("password").toString(),BCrypt.gensalt(SALT_ROUNDS))
                user["active"] = false
                user["role"] = 1
                Logger.log(LogLevel.DEBUG,"Prepared record to save registered user. " +
                        "Params: $user.","Users","register")
                user.save {
                    Logger.log(LogLevel.DEBUG,"Saved registered user. About to send activation email " +
                            "Params: $user.","Users","register")
                    app.smtpClient.init(user["email"].toString(),"Chatter Account Activation")
                    val result = app.smtpClient.sendMessage("Please, follow this link to activate your Chatter account "+
                            app.host+":"+app.port.toString()+"/activate/"+user["_id"])
                    addModel(user)
                    if (result) {
                        Logger.log(LogLevel.DEBUG,"Success sending registered user activation email. Return success result" +
                                "Params: $user.","Users","register")
                        callback(UserRegisterResultCode.RESULT_OK, user)
                    } else {
                        Logger.log(LogLevel.WARNING,"Could not send activation email." +
                                "Params: $user.","Users","register")
                        this.remove(user["_id"].toString()) {
                            Logger.log(LogLevel.WARNING,"Removed user with _id=${user["_id"]} because could not send" +
                                    "activation email","Users","register")
                            callback(UserRegisterResultCode.RESULT_ERROR_ACTIVATION_EMAIL, user)
                        }
                    }
                }
            }
        }
    }

    /**
     * Function which activates new registered user by link in activation email
     * @oaram user_id User id to activate
     * @return callback with activateion result code
     */
    fun activate(user_id:String,callback:(result_code:UserActivationResultCode)->Unit) {
        if (user_id.length==0) {
            callback(UserActivationResultCode.RESULT_ERROR_NO_USER)
        } else {
            val obj = getById(user_id)
            if (obj == null) {
                callback(UserActivationResultCode.RESULT_ERROR_NO_USER)
            } else {
                val user = obj as User
                var active = false;
                if (user["active"] != null) {
                    active = user["active"] as Boolean
                }
                if (active) {
                    callback(UserActivationResultCode.RESULT_ERROR_USER_ALREADY_ACTIVATED)
                } else {
                    user["active"] = true
                    user.save {
                        callback(UserActivationResultCode.RESULT_OK)
                    }
                }
            }
        }
    }

    /**
     * Function logins user to chat and creates session for him (if not already exist)
     *
     * @param login User name
     * @param password User password
     * @param callback callback with result of function
     * @return callback with result of operation and link to user model, if exist
     */
    fun login(login:String,password:String,callback:(result_code:UserLoginResultCode,user:User?)->Unit) {
        if(login.length==0) {
            callback(UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN,null)
        } else if (password.length==0) {
            callback(UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD,null)
        } else {
            val obj = ChatApplication.users.getBy("login",login)
            if (obj == null) {
                callback(UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN,null)
            } else {
                val user = obj as User
                if (!BCrypt.checkpw(password,user["password"].toString())) {
                    callback(UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD, null)
                } else if (!user.isActive()) {
                    callback(UserLoginResultCode.RESULT_ERROR_NOT_ACTIVATED,null)
                } else {
                    var session = ChatApplication.sessions.getBy("user_id",user["_id"].toString())
                    val currentTime = (System.currentTimeMillis()/1000).toInt()
                    if (session != null) {
                        val lastActivityTime = Integer.parseInt(session["lastActivityTime"].toString())
                        if (currentTime-lastActivityTime<USER_ACTIVITY_TIMEOUT) {
                            callback(UserLoginResultCode.RESULT_ERROR_ALREADY_LOGIN,null)
                            return
                        }
                    } else {
                        session = Session(db,"sessions",user)
                    }
                    session!!["loginTime"] = currentTime
                    session!!["lastActivityTime"] = currentTime
                    if (user["default_room"] != null && app.rooms.getById(user["default_room"].toString())!=null) {
                        session["room"] = user["default_room"].toString()
                    }
                    ChatApplication.sessions.addModel(session)
                    session.save{
                        callback(UserLoginResultCode.RESULT_OK,user)
                    }
                }
            }
        }
    }

    /**
     * Update parameters of existing user.
     *
     * @param params JSONObject with fields, which need to update
     * @return callback with result of operation (either RESULT_OK or error) and additional text
     * info about error
     */
    fun updateUser(params:JSONObject,callback:(result:UserUpdateResultCode,message:String)->Unit) {
        if (!params.contains("user_id")) {
            callback(UserUpdateResultCode.RESULT_ERROR_USER_NOT_SPECIFIED,"")
        } else {
            val obj = ChatApplication.users.getById(params.get("user_id").toString())
            if (obj == null) {
                callback(UserUpdateResultCode.RESULT_ERROR_USER_NOT_FOUND,"")
            } else {
                var user = obj as User
                if (params.contains("password")) {
                    if (params.get("password").toString().isEmpty()) {
                        callback(UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY, "password")
                        return
                    } else {
                        if (!params.contains("confirm_password") || params.get("confirm_password")!=params.get("password")) {
                            callback(UserUpdateResultCode.RESULT_ERROR_PASSWORDS_SHOULD_MATCH, "password")
                            return
                        } else {
                            user["password"] = BCrypt.hashpw(params.get("password").toString(),BCrypt.gensalt(SALT_ROUNDS))
                        }
                    }
                }
                if (params.contains("first_name")) {
                    if (params.get("first_name").toString().isEmpty()) {
                        callback(UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY, "first_name")
                        return
                    } else {
                        user["first_name"] = params.get("first_name").toString().trim()
                    }
                }
                if (params.contains("last_name")) {
                    if (params.get("last_name").toString().isEmpty()) {
                        callback(UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY, "last_name")
                        return
                    } else {
                        user["last_name"] = params.get("last_name").toString().trim();
                    }
                }
                if (params.contains("gender")) {
                    if (params.get("gender").toString().trim().isEmpty()) {
                        callback(UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY,"gender")
                        return;
                    } else if (!listOf("M","F").contains(params.get("gender").toString().trim())) {
                        callback(UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE,"gender")
                        return
                    } else {
                        user["gender"] = params.get("gender").toString().trim()
                    }
                }

                if (params.contains("birthDate")) {
                    if (params.get("birthDate").toString().trim().isEmpty()) {
                        callback(UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY,"birthDate")
                        return
                    } else {
                        var birthDate = 0
                        try {
                            birthDate = params.get("birthDate").toString().trim().toInt()
                        } catch (e:Exception) {}

                        if (birthDate==null ||
                                birthDate==0 ||
                                Date(birthDate.toLong()*1000).after(Date(System.currentTimeMillis()))) {
                            callback(UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE,"birthDate")
                            return
                        } else {
                            user["birthDate"] = birthDate
                        }
                    }
                }
                if (params.contains("default_room")) {
                    if (params.get("default_room").toString().trim().isEmpty()) {
                        callback(UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY,"default_room")
                        return
                    } else {
                        val room_id = params.get("default_room").toString().trim()
                        val obj = ChatApplication.rooms.getById(room_id)
                        if (obj == null) {
                            callback(UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE,"default_room")
                            return
                        } else {
                            user["default_room"] = room_id
                        }
                    }
                }
                if (params.contains("role")) {
                    if (params.get("role").toString().trim().isEmpty()) {
                        callback(UserUpdateResultCode.RESULT_ERROR_FIELD_IS_EMPTY,"role")
                        return
                    }
                    var role:UserRole? = null
                    try {
                        role = UserRole.valueOf(params.get("role").toString().trim())
                    } catch (e:Exception) {}
                    if (role == null) {
                        callback(UserUpdateResultCode.RESULT_ERROR_INCORRECT_FIELD_VALUE,"role")
                        return
                    }
                    user["role"] = role.value
                }
                user.save{
                    ChatApplication.users.addModel(user)
                    callback(UserUpdateResultCode.RESULT_OK,"")
                }
            }
        }
    }
    /**
     * User registration result codes
     */
    enum class UserRegisterResultCode {
        RESULT_OK,
        RESULT_ERROR_LOGIN_EXISTS,
        RESULT_ERROR_EMAIL_EXISTS,
        RESULT_ERROR_NO_LOGIN,
        RESULT_ERROR_NO_PASSWORD,
        RESULT_ERROR_NO_EMAIL,
        RESULT_ERROR_ACTIVATION_EMAIL,
        RESULT_ERROR_UNKNOWN;
        fun getMessage():String {
            var result = ""
            when(this) {
                RESULT_OK -> result = "You are registered. Activation email sent. Please, open it and activate your account"
                RESULT_ERROR_LOGIN_EXISTS -> result = "User with provided login already exists."
                RESULT_ERROR_EMAIL_EXISTS -> result = "User with provided email already exists."
                RESULT_ERROR_NO_LOGIN -> result = "Login is required."
                RESULT_ERROR_NO_PASSWORD -> result = "Password is required."
                RESULT_ERROR_NO_EMAIL -> result = "Email is required."
                RESULT_ERROR_ACTIVATION_EMAIL -> result = "Failed to send activation email. Please contact support."
                RESULT_ERROR_UNKNOWN -> result = "Unknown error. Please contact support"
            }
            return result
        }
    }

    /**
     * User activation result codes
     */
    enum class UserActivationResultCode {
        RESULT_OK,
        RESULT_ERROR_NO_USER,
        RESULT_ERROR_USER_ALREADY_ACTIVATED,
        RESULT_ERROR_UNKNOWN;
        fun getMessage(): String {
            var result = ""
            when (this) {
                RESULT_OK -> result = "Activation successful. You can login now."
                RESULT_ERROR_NO_USER -> result = "User account not found. Please, try to register again or contact support."
                RESULT_ERROR_USER_ALREADY_ACTIVATED -> result = "User account already activated. You can login now."
                RESULT_ERROR_UNKNOWN -> result = "Unknown error. Please, contact support."
            }
            return result
        }
        fun getHttpResponseCode(): Int {
            var result = 200
            when (this) {
                RESULT_OK -> result = 200
                RESULT_ERROR_NO_USER -> result = 406
                RESULT_ERROR_USER_ALREADY_ACTIVATED -> result = 409
                RESULT_ERROR_UNKNOWN -> result = 500
            }
            return result

        }
    }

    /**
     * User login result codes
     */
    enum class UserLoginResultCode {
        RESULT_OK,
        RESULT_ERROR_NOT_ACTIVATED,
        RESULT_ERROR_INCORRECT_LOGIN,
        RESULT_ERROR_INCORRECT_PASSWORD,
        RESULT_ERROR_ALREADY_LOGIN,
        RESULT_ERROR_UNKNOWN;
        fun getMessage(): String {
            var result = ""
            when (this) {
                RESULT_ERROR_NOT_ACTIVATED -> result = "Please, activate this account. Open activation email."
                RESULT_ERROR_INCORRECT_LOGIN -> result = "Incorrect login."
                RESULT_ERROR_INCORRECT_PASSWORD -> result = "Incorrect password."
                RESULT_ERROR_ALREADY_LOGIN -> result = "User already in the system."
                RESULT_ERROR_UNKNOWN -> result = "Unknown error. Please contact support."
            }
            return result;
        }
    }

    /**
     * User update result codes
     */
    enum class UserUpdateResultCode {
        RESULT_OK,
        RESULT_OK_PENDING_IMAGE_UPLOAD,
        RESULT_ERROR_IMAGE_UPLOAD,
        RESULT_ERROR_USER_NOT_SPECIFIED,
        RESULT_ERROR_USER_NOT_FOUND,
        RESULT_ERROR_FIELD_IS_EMPTY,
        RESULT_ERROR_INCORRECT_FIELD_VALUE,
        RESULT_ERROR_PASSWORDS_SHOULD_MATCH,
        RESULT_UNKNOWN;
        fun getMessage():String {
            var result = ""
            when(this) {
                RESULT_OK -> result = "Settings update successfully."
                RESULT_OK_PENDING_IMAGE_UPLOAD -> result = "Settings update successfully."
                RESULT_ERROR_IMAGE_UPLOAD -> result = "Error upload profile image. Please try again."
                RESULT_ERROR_USER_NOT_SPECIFIED -> result = "User not found. Please, contact support."
                RESULT_ERROR_FIELD_IS_EMPTY -> result = "Field is required."
                RESULT_ERROR_INCORRECT_FIELD_VALUE -> result = "Incorrect field value."
                RESULT_ERROR_PASSWORDS_SHOULD_MATCH -> result = "Passwords should match."
                RESULT_UNKNOWN -> result = "Unknown error. Please,contact support."
            }
            return result
        }
    }

    /**
     * User role definitions
     */
    enum class UserRole(val value:Int) {
        USER(1),
        ADMIN(2);
        companion object {
            /**
             * Function returns role enum by code of role
             *
             * @return UserRole
             */
            public fun getValueByCode(code:Int):UserRole {
                var result = USER
                when (code) {
                    1 -> result = USER
                    2 -> result = ADMIN
                }
                return result
            }
        }
    }
}
