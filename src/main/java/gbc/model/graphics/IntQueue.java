package gbc.model.graphics;

/**
 * Simple integer queue implementation for pixel FIFO.
 * Provides both throwing and non-throwing dequeue variants.
 */
public class IntQueue {
    private final int[] data;
    private int head;
    private int tail;
    private int size;

    public IntQueue(int capacity) {
        this.data = new int[capacity];
        this.head = 0;
        this.tail = 0;
        this.size = 0;
    }

    public void enqueue(int value) {
        if (size == data.length) {
            throw new IllegalStateException("Queue is full");
        }
        data[tail] = value;
        tail = (tail + 1) % data.length;
        size++;
    }

    public int dequeue() {
        if (size == 0) {
            throw new IllegalStateException("Queue is empty");
        }
        int value = data[head];
        head = (head + 1) % data.length;
        size--;
        return value;
    }

    /**
     * Dequeue without throwing on empty queue.
     * Returns defaultValue if the queue is empty.
     * Preferred for PPU hot paths to avoid exception overhead.
     */
    public int dequeueOrDefault(int defaultValue) {
        if (size == 0) {
            return defaultValue;
        }
        int value = data[head];
        head = (head + 1) % data.length;
        size--;
        return value;
    }

    public void clear() {
        head = 0;
        tail = 0;
        size = 0;
    }

    public int get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        return data[(head + index) % data.length];
    }

    public void set(int index, int value) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        data[(head + index) % data.length] = value;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean isFull() {
        return size == data.length;
    }
}
