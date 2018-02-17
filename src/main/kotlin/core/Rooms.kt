package core

import kotlinx.coroutines.experimental.launch

/**
 * Created by andrey on 2/17/18.
 */

object Rooms {
    lateinit var application: ChatApplication
    private var _rooms = HashMap<String,String>()

    fun loadRooms(callback:()->Unit) {
        _rooms.clear()
        val col = application.dBServer.db.getCollection("rooms")
        launch {
            val it = col.find().iterator()
            while (it.hasNext()) {
                val room = it.next()
                _rooms.set(room.get("_id").toString(), room.getString("name"))
            }
            callback()
        }
    }

    fun getRooms(): HashMap<String,String> {
        return _rooms
    }
}