package metaref.async;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChannelOps {

    public static class AltResult<T> {
        public final Channel<T> channel;
        public final T value;
        public final boolean isPut;

        AltResult(Channel<T> channel, T value, boolean isPut) {
            this.channel = channel;
            this.value = value;
            this.isPut = isPut;
        }
    }

    private static class Handler<T> implements Lock {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final Runnable callback;
        T value; // Store the value for take operations

        Handler(Runnable callback) {
            this.callback = callback;
        }

        boolean isActive() {
            return active.get();
        }

        Runnable commit() {
            if (active.compareAndSet(true, false)) {
                return callback;
            }
            return null;
        }

        @Override
        public void lock() {
            lock.lock();
        }

        @Override
        public void unlock() {
            lock.unlock();
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            lock.lockInterruptibly();
        }

        @Override
        public boolean tryLock() {
            return lock.tryLock();
        }

        @Override
        public boolean tryLock(long time, java.util.concurrent.TimeUnit unit) throws InterruptedException {
            return lock.tryLock(time, unit);
        }

        @Override
        public java.util.concurrent.locks.Condition newCondition() {
            return lock.newCondition();
        }
    }

    private static class FlagHandler<T> extends Handler<T> {
        private final AtomicBoolean flag;

        FlagHandler(AtomicBoolean flag, Runnable callback) {
            super(callback);
            this.flag = flag;
        }

        @Override
        Runnable commit() {
            if (flag.compareAndSet(true, false)) {
                return super.commit();
            }
            return null;
        }
    }

    private static final Random RANDOM = new Random();

    @SuppressWarnings("unchecked")
    public static <T> AltResult<T> alt(List<?> operations, boolean priority, boolean defaultOption) {
        AtomicBoolean flag = new AtomicBoolean(true);
        List<Handler<T>> handlers = new ArrayList<>(operations.size());

        while (true) {
            List<Integer> readyIndices = new ArrayList<>();

            // Setup phase
            for (int i = 0; i < operations.size(); i++) {
                Object op = operations.get(i);
                Handler<T> handler = new FlagHandler<>(flag, () -> {});

                if (op instanceof Channel) {
                    // Take operation
                    Channel<T> channel = (Channel<T>) op;
                    handlers.add(handler);
                    if (tryTake(channel, handler)) {
                        readyIndices.add(i);
                    }
                } else if (op instanceof List && ((List<?>) op).size() == 2) {
                    // Put operation
                    List<?> putOp = (List<?>) op;
                    Channel<T> channel = (Channel<T>) putOp.get(0);
                    T value = (T) putOp.get(1);
                    handlers.add(handler);
                    if (tryPut(channel, value, handler)) {
                        readyIndices.add(i);
                    }
                } else {
                    throw new IllegalArgumentException("Invalid operation: " + op);
                }
            }

            // Selection phase
            if (!readyIndices.isEmpty()) {
                int selectedIndex;
                if (priority) {
                    selectedIndex = readyIndices.get(0);
                } else {
                    selectedIndex = readyIndices.get(RANDOM.nextInt(readyIndices.size()));
                }
                AltResult<T> result = createResult(operations.get(selectedIndex), handlers.get(selectedIndex));
                cleanupHandlers(handlers);
                return result;
            } else if (defaultOption) {
                cleanupHandlers(handlers);
                return new AltResult<>(null, null, false);
            }

            // If we reach here, no operations were ready and defaultOption is false
            // We'll clean up the handlers and try again
            cleanupHandlers(handlers);
            handlers.clear();
            
            // Small delay to prevent busy-waiting
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    private static <T> void cleanupHandlers(List<Handler<T>> handlers) {
        for (Handler<T> handler : handlers) {
            if (handler.isActive()) {
                handler.commit(); // This will mark the handler as inactive
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> AltResult<T> createResult(Object op, Handler<T> handler) {
        if (op instanceof Channel) {
            // Take operation succeeded
            Channel<T> channel = (Channel<T>) op;
            return new AltResult<>(channel, handler.value, false);
        } else {
            // Put operation succeeded
            List<?> putOp = (List<?>) op;
            Channel<T> channel = (Channel<T>) putOp.get(0);
            T value = (T) putOp.get(1);
            return new AltResult<>(channel, value, true);
        }
    }

    private static <T> boolean tryTake(Channel<T> channel, Handler<T> handler) {
        T value = channel.tryTake();
        if (value != null) {
            handler.value = value; // Store the value in the handler
            Runnable callback = handler.commit();
            if (callback != null) {
                callback.run();
            }
            return true;
        }
        else if (channel.isClosed()) {
            handler.value = null; // Store null in the handler
            Runnable callback = handler.commit();
            if (callback != null) {
                callback.run();
            }
            return true;
        }
        return false;
    }

    private static <T> boolean tryPut(Channel<T> channel, T value, Handler<T> handler) {
        boolean success = channel.tryPut(value);
        if (success) {
            Runnable callback = handler.commit();
            if (callback != null) {
                callback.run();
            }
            return true;
        }
        return false;
    }
}