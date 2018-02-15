package models

data class User(var login: String,
                var password:String,
                var id:String,
                var email:String,
                var default_room:Int = 0,
                var confirm_token:String = "",
                var first_name:String="", var last_name:String="",
                var gender:String="",var birthDate:Int=0) {

    override fun toString() : String {
        return login+","+email
    }
}