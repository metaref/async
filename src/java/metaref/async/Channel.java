package metaref.async;

public interface Channel<E> {
    public boolean put(E e);
    public E take();
    public boolean close();
    public boolean isClosed();
    E tryTake();
    boolean tryPut(E e);
}
