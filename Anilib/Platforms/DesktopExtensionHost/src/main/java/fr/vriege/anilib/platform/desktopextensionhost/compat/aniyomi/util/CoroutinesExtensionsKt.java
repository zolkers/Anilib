package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.util;

import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.jvm.functions.Function2;

import kotlinx.coroutines.AwaitKt;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.Dispatchers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

/* Ordered parallel coroutine helpers, preserving suspension and parent cancellation. */
public final class CoroutinesExtensionsKt {
    private CoroutinesExtensionsKt() {}

    public static <A, B> Object parallelMap(
            Iterable<? extends A> values,
            Function2<? super A, ? super Continuation<? super B>, ?> transform,
            Continuation<? super List<B>> continuation) {
        var scope =
                CoroutineScopeKt.CoroutineScope(
                        continuation.getContext().plus(Dispatchers.getIO()));
        List<Deferred<B>> pending = new ArrayList<>();
        for (A value : values) {
            pending.add(
                    BuildersKt.async(
                            scope,
                            EmptyCoroutineContext.INSTANCE,
                            CoroutineStart.DEFAULT,
                            (child, result) -> transform.invoke(value, result)));
        }
        return AwaitKt.<B>awaitAll(pending, cast(continuation));
    }

    public static <A, B> List<B> parallelMapBlocking(
            Iterable<? extends A> values,
            Function2<? super A, ? super Continuation<? super B>, ?> transform) {
        try {
            return BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    (scope, c) -> parallelMap(values, transform, c));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Parallel mapping interrupted", exception);
        }
    }

    public static <A, B> Object parallelMapNotNull(
            Iterable<? extends A> values,
            Function2<? super A, ? super Continuation<? super B>, ?> transform,
            Continuation<? super List<B>> continuation) {
        Function<List<B>, List<B>> filter = list -> list.stream().filter(Objects::nonNull).toList();
        Object result = parallelMap(values, transform, mapping(continuation, filter));
        return result == IntrinsicsKt.getCOROUTINE_SUSPENDED()
                ? result
                : filter.apply(cast(result));
    }

    public static <A, B> List<B> parallelMapNotNullBlocking(
            Iterable<? extends A> values,
            Function2<? super A, ? super Continuation<? super B>, ?> transform) {
        return parallelMapBlocking(values, transform).stream().filter(Objects::nonNull).toList();
    }

    public static <A, B> Object parallelFlatMap(
            Iterable<? extends A> values,
            Function2<? super A, ? super Continuation<? super Iterable<B>>, ?> transform,
            Continuation<? super List<B>> continuation) {
        Object result =
                parallelMap(
                        values, transform, mapping(continuation, CoroutinesExtensionsKt::flatten));
        return result == IntrinsicsKt.getCOROUTINE_SUSPENDED() ? result : flatten(cast(result));
    }

    public static <A, B> List<B> parallelFlatMapBlocking(
            Iterable<? extends A> values,
            Function2<? super A, ? super Continuation<? super Iterable<B>>, ?> transform) {
        return flatten(parallelMapBlocking(values, transform));
    }

    public static <A, B> Object parallelCatchingFlatMap(
            Iterable<? extends A> values,
            Function2<? super A, ? super Continuation<? super Iterable<B>>, ?> transform,
            Continuation<? super List<B>> continuation) {
        return parallelFlatMap(values, recovering(transform), continuation);
    }

    public static <A, B> List<B> parallelCatchingFlatMapBlocking(
            Iterable<? extends A> values,
            Function2<? super A, ? super Continuation<? super Iterable<B>>, ?> transform) {
        return parallelFlatMapBlocking(values, recovering(transform));
    }

    private static <A, B> Function2<A, Continuation<? super Iterable<B>>, Object> recovering(
            Function2<? super A, ? super Continuation<? super Iterable<B>>, ?> transform) {
        return (value, continuation) -> {
            Continuation<Iterable<B>> safe =
                    new Continuation<>() {
                        @Override
                        public CoroutineContext getContext() {
                            return continuation.getContext();
                        }

                        @Override
                        public void resumeWith(Object result) {
                            try {
                                ResultKt.throwOnFailure(result);
                            } catch (CancellationException cancelled) {
                                continuation.resumeWith(result);
                                return;
                            } catch (Throwable failure) {
                                result = List.of();
                            }
                            continuation.resumeWith(result);
                        }
                    };
            try {
                return transform.invoke(value, safe);
            } catch (CancellationException cancelled) {
                throw cancelled;
            } catch (Exception failure) {
                return List.of();
            }
        };
    }

    private static <A, B> Continuation<A> mapping(
            Continuation<? super B> target, Function<A, B> transform) {
        return new Continuation<>() {
            @Override
            public CoroutineContext getContext() {
                return target.getContext();
            }

            @Override
            public void resumeWith(Object result) {
                try {
                    ResultKt.throwOnFailure(result);
                    target.resumeWith(transform.apply(cast(result)));
                } catch (Throwable failure) {
                    target.resumeWith(ResultKt.createFailure(failure));
                }
            }
        };
    }

    private static <B> List<B> flatten(List<? extends Iterable<B>> values) {
        List<B> result = new ArrayList<>();
        values.forEach(group -> group.forEach(result::add));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
