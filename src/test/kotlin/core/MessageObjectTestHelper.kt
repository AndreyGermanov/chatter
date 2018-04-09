package core


import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage
import org.eclipse.jetty.websocket.api.annotations.WebSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

interface WSEchoSocketDelegate {
    var webSocketResponse: String
}
/**
 * Created by andrey on 2/24/18.
 */
@WebSocket
class SimpleEchoSocket() {
    private val closeLatch: CountDownLatch
    private var session: Session? = null
    lateinit var delegate:WSEchoSocketDelegate
    var lastResponse = ""

    init {
        this.closeLatch = CountDownLatch(1)
    }

    @Throws(InterruptedException::class)
    fun awaitClose(duration: Int, unit: TimeUnit): Boolean {
        return this.closeLatch.await(duration.toLong(), unit)
    }

    @OnWebSocketClose
    fun onClose(statusCode: Int, reason: String) {
        this.session = null
        this.closeLatch.countDown() // trigger latch
    }

    @OnWebSocketConnect
    fun onConnect(session: Session) {
        this.session = session
    }

    @OnWebSocketMessage
    fun onMessage(msg: String) {
        lastResponse = msg
        delegate.webSocketResponse = msg
    }
}
