package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;

final class ExtensionOperationDispatcher {
    private static final String COMPATIBILITY_PACKAGE =
            "fr.vriege.anilib.platform.desktopextensionhost.compat.";
    private static final Duration SUSPEND_TIMEOUT = Duration.ofSeconds(45);

    private ExtensionOperationDispatcher() {
    }

    static Invocation modernOrRx(
            Object source,
            String suspendMethod,
            String reactiveMethod,
            Object... arguments) {
        Optional<Method> suspend = extensionSuspendMethod(source.getClass(), suspendMethod, arguments);
        if (suspend.isPresent()) {
            return new Invocation(true, invokeSuspend(source, suspend.orElseThrow(), arguments));
        }
        Optional<Method> reactive = extensionMethod(source.getClass(), reactiveMethod, arguments);
        return reactive.map(method -> new Invocation(true, await(invoke(source, method, arguments))))
                .orElseGet(() -> new Invocation(false, null));
    }

    static Invocation suspend(Object source, String methodName, Object... arguments) {
        return extensionSuspendMethod(source.getClass(), methodName, arguments)
                .map(method -> new Invocation(true, invokeSuspend(source, method, arguments)))
                .orElseGet(() -> new Invocation(false, null));
    }

    static Invocation ordinary(Object source, String methodName, Object... arguments) {
        return extensionMethod(source.getClass(), methodName, arguments)
                .map(method -> new Invocation(true, invoke(source, method, arguments)))
                .orElseGet(() -> new Invocation(false, null));
    }

    static Object invokeAny(Object target, String methodName, Object... arguments) {
        Method method = compatibleMethod(target.getClass(), methodName, arguments)
                .orElseThrow(() -> new AbiException("Extension ABI method is unavailable: " + methodName));
        return invoke(target, method, arguments);
    }

    static boolean hasClassicImplementation(Object source, String methodName, Object... arguments) {
        return extensionMethod(source.getClass(), methodName, arguments).isPresent();
    }

    static boolean hasExtensionMethod(Object source, String methodName, int parameterCount) {
        for (Class<?> current = source.getClass(); current != null; current = current.getSuperclass()) {
            if (current.getName().startsWith(COMPATIBILITY_PACKAGE)) {
                continue;
            }
            if (Arrays.stream(current.getDeclaredMethods()).anyMatch(method -> method.getName().equals(methodName)
                    && method.getParameterCount() == parameterCount)) {
                return true;
            }
        }
        return false;
    }

    static boolean supportsHosters(Object source) {
        return extensionSuspendMethod(source.getClass(), "getHosterList", new Object[]{null}).isPresent()
                || declaredExtensionMethod(source.getClass(), "hosterListParse");
    }

    static <T> T result(Object value, Class<T> type) {
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        throw new AbiException("Extension operation returned "
                + (value == null ? "null" : value.getClass().getName()) + " instead of " + type.getName());
    }

    static <T> List<T> listResult(Object value, Class<T> elementType) {
        if (!(value instanceof List<?> values)) {
            throw new AbiException("Extension operation did not return a list");
        }
        return values.stream().map(item -> result(item, elementType)).toList();
    }

    private static Object await(Object value) {
        if (value == null) {
            return null;
        }
        Optional<Method> toBlocking = compatibleMethod(value.getClass(), "toBlocking", new Object[0]);
        if (toBlocking.isEmpty()) {
            return value;
        }
        Object blocking = invoke(value, toBlocking.orElseThrow(), new Object[0]);
        return invokeAny(blocking, "single");
    }

    private static Object invokeSuspend(Object target, Method method, Object[] arguments) {
        CompletableFuture<Object> resumed = new CompletableFuture<>();
        Continuation<Object> continuation = new Continuation<>() {
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(Object value) {
                try {
                    ResultKt.throwOnFailure(value);
                    resumed.complete(value);
                } catch (RuntimeException | Error failure) {
                    resumed.completeExceptionally(failure);
                }
            }
        };
        Object[] invocation = Arrays.copyOf(arguments, arguments.length + 1);
        invocation[arguments.length] = continuation;
        Object result = invoke(target, method, invocation);
        if (result != IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
            return result;
        }
        try {
            return resumed.get(SUSPEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Extension operation timed out: " + method.getName(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Extension operation was interrupted: " + method.getName(), exception);
        } catch (ExecutionException exception) {
            throw propagate(exception.getCause());
        }
    }

    private static Object invoke(Object target, Method method, Object[] arguments) {
        try {
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw new AbiException("Extension ABI method is inaccessible: " + method.getName(), exception);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause() == null ? exception : exception.getCause());
        }
    }

    private static Optional<Method> extensionSuspendMethod(
            Class<?> type,
            String methodName,
            Object[] arguments) {
        Object[] invocation = Arrays.copyOf(arguments, arguments.length + 1);
        invocation[arguments.length] = ContinuationMarker.INSTANCE;
        return compatibleMethod(type, methodName, invocation).filter(ExtensionOperationDispatcher::extensionOwned);
    }

    private static Optional<Method> extensionMethod(Class<?> type, String methodName, Object[] arguments) {
        return compatibleMethod(type, methodName, arguments).filter(ExtensionOperationDispatcher::extensionOwned);
    }

    private static Optional<Method> compatibleMethod(Class<?> type, String methodName, Object[] arguments) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName)
                        && compatible(method.getParameterTypes(), arguments)) {
                    return Optional.of(method);
                }
            }
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && compatible(method.getParameterTypes(), arguments)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private static boolean compatible(Class<?>[] parameters, Object[] arguments) {
        if (parameters.length != arguments.length) {
            return false;
        }
        for (int index = 0; index < parameters.length; index++) {
            Object argument = arguments[index];
            Class<?> parameter = parameters[index];
            if (argument == ContinuationMarker.INSTANCE) {
                if (!(parameter.getName().equals("kotlin.coroutines.Continuation") || parameter == Object.class)) {
                    return false;
                }
            } else if (argument == null) {
                if (parameter.isPrimitive()) {
                    return false;
                }
            } else if (!boxed(parameter).isInstance(argument)) {
                return false;
            }
        }
        return true;
    }

    private static boolean extensionOwned(Method method) {
        return !method.getDeclaringClass().getName().startsWith(COMPATIBILITY_PACKAGE);
    }

    private static boolean declaredExtensionMethod(Class<?> type, String methodName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().startsWith(COMPATIBILITY_PACKAGE)) {
                continue;
            }
            if (Arrays.stream(current.getDeclaredMethods()).anyMatch(method -> method.getName().equals(methodName))) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> boxed(Class<?> value) {
        if (!value.isPrimitive()) {
            return value;
        }
        if (value == int.class) return Integer.class;
        if (value == long.class) return Long.class;
        if (value == boolean.class) return Boolean.class;
        if (value == float.class) return Float.class;
        if (value == double.class) return Double.class;
        if (value == short.class) return Short.class;
        if (value == byte.class) return Byte.class;
        if (value == char.class) return Character.class;
        return Void.class;
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Extension operation failed", failure);
    }

    record Invocation(boolean available, Object value) {
    }

    static final class AbiException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private AbiException(String message) {
            super(message);
        }

        private AbiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private enum ContinuationMarker {
        INSTANCE
    }
}
