/**
 * Created by Andrey Germanov on 2/18/18.
 */
package interactors

import com.mongodb.client.MongoDatabase
import core.ChatApplication
import core.MessageCenter
import models.Room
import org.bson.Document
import models.User
import models.Session
import org.bson.types.ObjectId
import org.json.simple.JSONObject
import utils.BCrypt
import utils.LogLevel
import utils.Logger
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
     * Function sends activation email to specified user and returns status of operation
     *
     * @param user : User model to which email send message
     * @returns True if email send successfuly or False otherwise
     */
    fun sendActivationEmail(user:User):Boolean {
        app.smtpClient.init(user["email"].toString(),"Chatter Account Activation")
        val result = app.smtpClient.sendMessage("Please, follow this link to activate your Chatter account "+
                app.host+":"+app.port.toString()+"/activate/"+user["_id"])
        return result
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
                    val result = this.sendActivationEmail(user)
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
            var user = ChatApplication.users.getById(login) as? User
            var loginBySessionId = false;
            if (user!=null) {
                val session = ChatApplication.sessions.getById(password) as? Session
                if (session==null) {
                    callback(UserLoginResultCode.RESULT_ERROR_SESSION_TIMEOUT,null)
                    return
                }
                val currentTime = (System.currentTimeMillis()/1000).toInt()
                if (currentTime - session["lastActivityTime"].toString().toInt() > MessageCenter.SESSION_TIMEOUT) {
                    callback(UserLoginResultCode.RESULT_ERROR_SESSION_TIMEOUT,null)
                    return
                }
                loginBySessionId = true;
            }
            if (user == null) {
                user = ChatApplication.users.getBy("login",login) as? User
            }
            if (user == null) {
                callback(UserLoginResultCode.RESULT_ERROR_INCORRECT_LOGIN,null)
            } else {
                if (!BCrypt.checkpw(password,user["password"].toString()) && !loginBySessionId) {
                    callback(UserLoginResultCode.RESULT_ERROR_INCORRECT_PASSWORD, null)
                } else if (!user.isActive()) {
                    callback(UserLoginResultCode.RESULT_ERROR_NOT_ACTIVATED,null)
                } else {
                    var session = ChatApplication.sessions.getBy("user_id",user["_id"].toString())
                    val currentTime = (System.currentTimeMillis()/1000).toInt()
                    if (session != null) {
                        if (!loginBySessionId) {
                            val lastActivityTime = Integer.parseInt(session["lastActivityTime"].toString())
                            if (currentTime - lastActivityTime < USER_ACTIVITY_TIMEOUT) {
                                callback(UserLoginResultCode.RESULT_ERROR_ALREADY_LOGIN, null)
                                return
                            }
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
     * Function used to remove users with specified ids from database and from collection
     * including profile images
     *
     * @returns Number of removed users
     */
    fun removeUsers(user_ids:ArrayList<String>):Int {
        var result = 0;
        for (user_id in user_ids) {
            if (this.getById(user_id)==null) {
                continue
            }
            app.users.remove(user_id) {}
            Files.deleteIfExists(Paths.get(app.usersPath+"/"+user_id+"/profile.png"))
            try {
                Files.deleteIfExists(Paths.get(app.usersPath+"/"+user_id))
            } catch (e:Exception) {}
            var sessions = ChatApplication.sessions.getListBy("user_id",user_id)
            if (sessions==null || sessions.count()==0) {
                continue
            }
            var it = sessions.iterator()
            while (it.hasNext()) {
                var session = it.next() as? Session
                if (session==null || session["_id"]==null) {
                    continue
                }
                app.sessions.remove(session["_id"].toString()){}
            }
            result++
        }
        return result
    }

    /**
     * Function calculates string representation of field with provided [field_id]
     * of provided [model]
     *
     * @param model: Model from which need to get field
     * @param field_id: ID of field which need to extract and get representation
     * @return Human readable string representation of field value
     */
    override fun getFieldPresentation(obj:Any,field_id:String): String {
        val model = obj as? User
        if (model == null) {
            Logger.log(LogLevel.WARNING, "Could not convert object to model to get text presentation " +
                    "for field '$field_id'", "Users","getFieldPresentation")
            return super.getFieldPresentation(obj, field_id)
        }
        if (model[field_id] == null) {
            Logger.log(LogLevel.WARNING, "Could not find '$field_id' field in 'User' model " +
                    "for field '$field_id'", "Users","getFieldPresentation")
            return ""

        }
        if (field_id == "default_room") {
            val room = ChatApplication.rooms.getById(model["default_room"].toString()) as? Room
            if (room == null) {
                Logger.log(LogLevel.WARNING, "Could not find room to get presentation for 'default_room' " +
                        "field", "Users","getFieldPresentation")
                return ""

            }
            return room["name"].toString()
        }
        if (field_id == "active") {
            val active = model["active"] as? Boolean
            if (active == null || !active) {
                return "Inactive"
            } else {
                return "Active"
            }
        }
        if (field_id == "role") {
            var role = 1
            try {
                role = model["role"].toString().toInt()
            } catch (e:Exception) {
                Logger.log(LogLevel.WARNING, "Could not convert 'role' field value to Integer from " +
                        "'${model["role"]}' ", "Users","getFieldPresentation")
            }
            if (role == 1) {
                return "User"
            } else if (role == 2) {
                return "Admin"
            } else {
                return "User"
            }
        }
        if (field_id == "birthDate") {
            var birthDate:Long = 0
            try {
                birthDate = model["birthDate"].toString().toLong()
            } catch (e:Exception) {
                Logger.log(LogLevel.WARNING, "Could not convert 'birthDate' field value to Long from " +
                        "'${model["birthDate"]}' ", "Users","getFieldPresentation")
                return ""
            }
            if (birthDate<=0 || birthDate > System.currentTimeMillis()/1000) {
                Logger.log(LogLevel.WARNING, "Could not convert 'birthDate' field value to correct date from " +
                        "'${model["birthDate"]}' ", "Users","getFieldPresentation")
                return ""
            }
            return Logger.formatDate(Date(birthDate))
        }
        if (field_id == "gender") {
            val gender = model["gender"].toString()
            if (gender == "M") {
                return "Male"
            } else if (gender == "F") {
                return "Female"
            } else {
                return ""
            }
        }
        return super.getFieldPresentation(obj, field_id)
    }

    /**
     * Function calculates sort order for models [obj1] and [obj2] when sorting using [sortOrder] param
     *
     * @param obj1: First model
     * @param obj2: Second model
     * @param sortOrder: Pair<String,String> which contains sorting rule. First part is field_id,second part is
     * sort direction: ASC or DESC
     * @returns When sort order is ASC, returns 1 if obj1>obj2, -1 if obj1<obj2 and 0 if they are equals
     *          When sort order is DESC, returns 1 if obj1<obj2, -1 if obj1>obj2 and 0 if they are equals
     *          Else returns null in case of error during operation
     */
    override fun getSortOrder(obj1:Any, obj2:Any,sortOrder:Pair<String,String>): Int? {
        var result = super.getSortOrder(obj1, obj2, sortOrder)
        if (result == null) {
            return result
        }
        val model1 = obj1 as? User
        val model2 = obj2 as? User
        if (model1 == null) {
            Logger.log(LogLevel.WARNING,"Object 1 ($obj1) is not correct User model",
                    "Users","getSortOrder")
            return null
        }
        if (model2 == null) {
            Logger.log(LogLevel.WARNING,"Object 2 ($obj2) is not correct User model",
                    "Users","getSortOrder")
            return null
        }
        var corrector = 1
        if (sortOrder.second == "DESC") {
            corrector = -1
        }
        when (sortOrder.first) {
            "default_room" -> {
                val room1_name = (ChatApplication.rooms.getById(
                        model1["default_room"]?.toString() ?: "") as? Room)?.get("name")?.toString()
                        ?: ""
                val room2_name = (ChatApplication.rooms.getById(
                        model2["default_room"]?.toString() ?: "") as? Room)?.get("name")?.toString()
                        ?: ""
                if (room1_name>room2_name) {
                    result = 1*corrector
                } else if (room1_name<room2_name) {
                    result = -1*corrector
                } else {
                    result = 0
                }
            }
            "active", "role" -> {
                val v1 = getFieldPresentation(model1,sortOrder.first)
                val v2 = getFieldPresentation(model2,sortOrder.first)
                if (v1>v2) {
                    result = 1*corrector
                } else if (v1<v2) {
                    result = -1*corrector
                } else {
                    result = 0
                }
            }
        }
        return result
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
        RESULT_ERROR_SESSION_TIMEOUT,
        RESULT_ERROR_UNKNOWN;
        fun getMessage(): String {
            var result = ""
            when (this) {
                RESULT_ERROR_NOT_ACTIVATED -> result = "Please, activate this account. Open activation email."
                RESULT_ERROR_INCORRECT_LOGIN -> result = "Incorrect login."
                RESULT_ERROR_INCORRECT_PASSWORD -> result = "Incorrect password."
                RESULT_ERROR_ALREADY_LOGIN -> result = "User already in the system."
                RESULT_ERROR_UNKNOWN -> result = "Unknown error. Please contact support."
                RESULT_ERROR_SESSION_TIMEOUT -> result = "Session timeout. Please, login again."
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
