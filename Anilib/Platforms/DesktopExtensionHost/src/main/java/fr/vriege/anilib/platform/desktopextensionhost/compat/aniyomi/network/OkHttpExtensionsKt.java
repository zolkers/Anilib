package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;

import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.IntrinsicsKt;

import kotlinx.coroutines.CancellableContinuationImpl;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import rx.Observable;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class OkHttpExtensionsKt {
    private OkHttpExtensionsKt() {}

    public static Object await(Call call, Continuation<? super Response> continuation) {
        return awaitResponse(call, continuation, false);
    }

    public static Object awaitSuccess(Call call, Continuation<? super Response> continuation) {
        return awaitResponse(call, continuation, true);
    }

    private static Object awaitResponse(
            Call call, Continuation<? super Response> continuation, boolean success) {
        if (continuation == null) return execute(call, success);
        CancellableContinuationImpl<Response> pending =
                new CancellableContinuationImpl<>(IntrinsicsKt.intercepted(continuation), 1);
        pending.initCancellability();
        pending.invokeOnCancellation(
                cause -> {
                    call.cancel();
                    return Unit.INSTANCE;
                });
        call.enqueue(
                new Callback() {
                    @Override
                    public void onFailure(Call source, IOException failure) {
                        pending.resumeWith(ResultKt.createFailure(failure));
                    }

                    @Override
                    public void onResponse(Call source, Response response) {
                        if (success && !response.isSuccessful()) {
                            response.close();
                            pending.resumeWith(
                                    ResultKt.createFailure(
                                            new IOException(
                                                    "Source request failed with HTTP "
                                                            + response.code())));
                        } else {
                            pending.resume(
                                    response,
                                    (cause, value, context) -> {
                                        value.close();
                                        return Unit.INSTANCE;
                                    });
                        }
                    }
                });
        return pending.getResult();
    }

    public static Observable<Response> asObservable(Call call) {
        return Observable.fromCallable(() -> execute(call, false));
    }

    public static Observable<Response> asObservableSuccess(Call call) {
        return Observable.fromCallable(() -> execute(call, true));
    }

    private static Response execute(Call call, boolean requireSuccess) {
        try {
            Response response = call.execute();
            if (requireSuccess && !response.isSuccessful()) {
                int status = response.code();
                response.close();
                throw new IllegalStateException("Source request failed with HTTP " + status);
            }
            return response;
        } catch (IOException exception) {
            throw new UncheckedIOException("Source request failed", exception);
        }
    }
}
