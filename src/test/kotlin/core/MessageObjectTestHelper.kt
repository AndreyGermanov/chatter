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

/**
 * Created by andrey on 2/24/18.
 */
@WebSocket
class SimpleEchoSocket() {
    private val closeLatch: CountDownLatch
    private var session: Session? = null
    lateinit var delegate:MessageObjectTest

    init {
        this.closeLatch = CountDownLatch(1)
    }

    @Throws(InterruptedException::class)
    fun awaitClose(duration: Int, unit: TimeUnit): Boolean {
        return this.closeLatch.await(duration.toLong(), unit)
    }

    @OnWebSocketClose
    fun onClose(statusCode: Int, reason: String) {
        System.out.printf("Connection closed: %d - %s%n", statusCode, reason)
        this.session = null
        this.closeLatch.countDown() // trigger latch
    }

    @OnWebSocketConnect
    fun onConnect(session: Session) {
        System.out.printf("Got connect: %s%n", session)
        delegate.webSocketSession = session
        this.session = session
        try {
            var fut: Future<Void>
            fut = session.getRemote().sendStringByFuture("Hello")
            fut.get(2, TimeUnit.SECONDS) // wait for send to complete.

            fut = session.getRemote().sendStringByFuture("Thanks for the conversation.")
            fut.get(2, TimeUnit.SECONDS) // wait for send to complete.

            session.close(StatusCode.NORMAL, "I'm done")
        } catch (t: Throwable) {
            t.printStackTrace()
        }

    }

    @OnWebSocketMessage
    fun onMessage(msg: String) {
        delegate.webSocketResponse = msg
        System.out.printf("Got msg: %s%n", msg)
    }
}
