package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;

import fr.vriege.anilib.framework.concurrent.runtime.ManagedExecutors;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.quickjs.QuickJs;

import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.IntrinsicsKt;

import kotlinx.coroutines.CancellableContinuationImpl;

public final class JavaScriptEngine {
    public JavaScriptEngine(Context context) {}

    public <T> Object evaluate(String script, Continuation<? super T> continuation) {
        CancellableContinuationImpl<T> result =
                new CancellableContinuationImpl<>(IntrinsicsKt.intercepted(continuation), 1);
        result.initCancellability();
        var task =
                ManagedExecutors.supply(
                        "extension-javascript",
                        () -> {
                            try (QuickJs engine = QuickJs.create()) {
                                return engine.evaluate(script);
                            }
                        });
        result.invokeOnCancellation(
                cause -> {
                    task.cancel(true);
                    return Unit.INSTANCE;
                });
        task.whenComplete(
                (value, failure) ->
                        result.resumeWith(
                                failure == null ? value : ResultKt.createFailure(failure)));
        return result.getResult();
    }
}
