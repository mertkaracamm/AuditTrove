package com.audittrove.audit;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

/**
 * İptal edilen incelemenin model çağrılarını da durdurur. İnceleme birden çok havuza dağıldığı için
 * bayrak iş parçacığına iliştirilir ve dağıtım havuzları sarmalanarak alt işlere taşınır.
 *
 * Kullanım: inceleme başlarken {@link #run}, model çağrısından hemen önce {@link #check}.
 */
public final class CancelScope {
    private CancelScope() {}

    private static final ThreadLocal<BooleanSupplier> CURRENT = new ThreadLocal<>();

    /** İptal bayrağı bu iş parçacığına bağlıyken gövdeyi çalıştırır; bitince temizler. */
    public static <T> T run(BooleanSupplier cancelled, java.util.function.Supplier<T> body) {
        BooleanSupplier previous = CURRENT.get();
        CURRENT.set(cancelled);
        try {
            return body.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static boolean cancelled() {
        BooleanSupplier flag = CURRENT.get();
        return flag != null && flag.getAsBoolean();
    }

    /** Model çağrısından önce çağrılır: kullanıcı vazgeçtiyse yeni çağrı yapılmaz. */
    public static void check() {
        if (cancelled()) throw new AuditCancelledException();
    }

    /** Havuza verilen işler bayrağı devralsın diye sarmalayıcı. */
    public static ExecutorService inherit(ExecutorService delegate) {
        return new InheritingExecutorService(delegate, delegate);
    }

    private record InheritingExecutorService(ExecutorService delegate, ExecutorService self)
            implements ExecutorService {

        private static Runnable wrap(Runnable task, BooleanSupplier flag) {
            return () -> run(flag, () -> { task.run(); return null; });
        }

        private static <T> Callable<T> wrap(Callable<T> task, BooleanSupplier flag) {
            return () -> {
                try {
                    return run(flag, () -> {
                        try {
                            return task.call();
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    });
                } catch (IllegalStateException e) {
                    if (e.getCause() instanceof Exception cause) throw cause;
                    throw e;
                }
            };
        }

        @Override public void execute(Runnable command) { delegate.execute(wrap(command, CURRENT.get())); }
        @Override public <T> Future<T> submit(Callable<T> task) { return delegate.submit(wrap(task, CURRENT.get())); }
        @Override public <T> Future<T> submit(Runnable task, T result) { return delegate.submit(wrap(task, CURRENT.get()), result); }
        @Override public Future<?> submit(Runnable task) { return delegate.submit(wrap(task, CURRENT.get())); }
        @Override public void shutdown() { delegate.shutdown(); }
        @Override public java.util.List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
        @Override public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return delegate.invokeAll(tasks);
        }
        @Override public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks, long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
            return delegate.invokeAll(tasks, timeout, unit);
        }
        @Override public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks) throws InterruptedException, java.util.concurrent.ExecutionException {
            return delegate.invokeAny(tasks);
        }
        @Override public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks, long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException, java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException {
            return delegate.invokeAny(tasks, timeout, unit);
        }
    }
}
