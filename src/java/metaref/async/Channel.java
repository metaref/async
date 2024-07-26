package metaref.async;

public interface Channel<E> {
    public boolean put(E e);
    public E take();
    public boolean close();
    public boolean isClosed();
    public int capacity();
    E tryTake();
    boolean tryPut(E e);
}
