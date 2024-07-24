package metaref.async;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class CSPChannel<E> implements Channel<E> {
    private final Queue<E> buffer;
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private boolean closed = false;
    private E pendingItem = null;

    public CSPChannel() {
        this(0);
    }

    public CSPChannel(int capacity) {
        this.capacity = capacity;
        this.buffer = new LinkedList<>();
    }

    @Override
    public boolean put(E e) {
        lock.lock();
        try {
            while (!closed && ((capacity > 0 && buffer.size() == capacity) || (capacity == 0 && pendingItem != null))) {
                notFull.await();
            }
            if (closed) {
                return false;
            }
            if (capacity == 0) {
                pendingItem = e;
                notEmpty.signal();
                while (pendingItem != null && !closed) {
                    notFull.await();
                }
                return pendingItem == null;
            } else {
                buffer.offer(e);
                notEmpty.signal();
                return true;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E take() {
        lock.lock();
        try {
            while (buffer.isEmpty() && pendingItem == null && !closed) {
                notEmpty.await();
            }
            if (pendingItem != null) {
                E item = pendingItem;
                pendingItem = null;
                notFull.signal();
                return item;
            } else if (!buffer.isEmpty()) {
                E item = buffer.poll();
                notFull.signal();
                return item;
            }
            return null; // Returns null if the channel is closed and empty
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean close() {
        lock.lock();
        try {
            if (!closed) {
                closed = true;
                notEmpty.signalAll();
                notFull.signalAll();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isClosed() {
        lock.lock();
        try {
            return closed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E tryTake() {
        lock.lock();
        try {
            if (pendingItem != null) {
                E item = pendingItem;
                pendingItem = null;
                notFull.signal();
                return item;
            } else if (!buffer.isEmpty()) {
                E item = buffer.poll();
                notFull.signal();
                return item;
            }
            return null; // Returns null if the channel is closed and empty
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean tryPut(E e) {
        lock.lock();
        try {
            if (capacity == 0) {
                if (pendingItem == null && !closed) {
                    pendingItem = e;
                    notEmpty.signal();
                    return true;
                }
            } else if (buffer.size() < capacity && !closed) {
                buffer.offer(e);
                notEmpty.signal();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
}