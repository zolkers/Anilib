package fr.vriege.anilib.platform.desktop

import fr.vriege.anilib.platform.compose.BrowserRuntimeStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BrowserRuntimeStatusTest {
    @Test
    fun deferredRuntimeStartsOnlyOnceWhenFirstResolved() {
        val starts = AtomicInteger()
        val runtime = BrowserRuntimeStatus.deferred {
            starts.incrementAndGet()
            BrowserRuntimeStatus.ready()
        }

        assertNull(runtime.currentOrNull())
        val first = runtime.resolve()
        val second = runtime.resolve()

        assertTrue(first.available)
        assertSame(first, second)
        assertSame(first, runtime.currentOrNull())
        assertTrue(starts.get() == 1)
    }

    @Test
    fun unavailableRuntimeDoesNotRequireInitialization() {
        val runtime = BrowserRuntimeStatus.unavailable("missing")

        assertFalse(runtime.available)
        assertSame(runtime, runtime.currentOrNull())
        assertSame(runtime, runtime.resolve())
    }
}
