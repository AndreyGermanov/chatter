package core

import io.javalin.Javalin
import org.json.simple.JSONObject
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before

/**
 * Created by andrey on 2/25/18.
 */
class MessageCenterTest {

    val app = ChatApplication

    @Before
    fun setUp() {
        app.webServer = Javalin.create()
        app.msgServer = MessageCenter()
    }

    @Test
    fun processFileRequestsQueue() {
        val request = JSONObject(mapOf("request_id" to "req1","request_timestamp" to (System.currentTimeMillis()/1000).toInt()))
        val req = mapOf(
                "session" to null,
                "request" to request) as HashMap<*, *>
        app.msgServer.file_requests.set(123456789,req)
        app.msgServer.file_requests.set(123456788,req)
        app.msgServer.processFileRequestsQueue()
        assertEquals("In the beginning should be"+app.msgServer.file_requests.count().toString()+" items",2,app.msgServer.file_requests.count())
        Thread.sleep((app.msgServer.PENDING_REQUEST_TIMEOUT*1000+1000).toLong())
        app.msgServer.processFileRequestsQueue()
        assertEquals("Queue should be empty after cleanup",0,app.msgServer.file_requests.count())
    }

}