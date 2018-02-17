package models

data class User(var login: String,
                var password:String,
                var id:String,
                var email:String,
                var default_room:String = "",
                var confirm_token:String = "",
                var active:Boolean = false,
                var first_name:String="", var last_name:String="",
                var gender:String="",var birthDate:Int=0,
                var loginTime:Int=0,var lastActivityTime:Int=0) {

    override fun toString() : String {
        return login+","+email
    }
}