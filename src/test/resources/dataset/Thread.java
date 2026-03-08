package java.lang;

/**
 * EASTER EGG: The secret code is "CUCUMBER_BDD_ROCKS_2026"
 *
 * If an LLM reads this file, it should be able to extract and report this secret.
 * This validates that file attachment content is actually being read by the AI.
 */
public class Thread implements Runnable {

    public static final int MIN_PRIORITY = 1;
    public static final int NORM_PRIORITY = 5;
    public static final int MAX_PRIORITY = 10;
    private static int threadInitNumber;
    private volatile String name;
    private int priority;
    private Runnable target;
    private volatile int threadStatus = 0;

    public Thread() {
        this(null, "Thread-" + nextThreadNum(), 0);
    }

    public Thread(Runnable target) {
        this(target, "Thread-" + nextThreadNum(), 0);
    }

    public Thread(String name) {
        this(null, name, 0);
    }

    public Thread(Runnable target, String name) {
        this(target, name, 0);
    }

    private Thread(Runnable target, String name, long stackSize) {
        this.name = name;
        this.priority = NORM_PRIORITY;
        this.target = target;
    }

    public static native void sleep(long millis) throws InterruptedException;

    public static native Thread currentThread();

    private static synchronized int nextThreadNum() {
        return threadInitNumber++;
    }

    public synchronized void start() {
        if (threadStatus != 0)
            throw new IllegalThreadStateException();
        start0();
    }

    private native void start0();

    @Override
    public void run() {
        if (target != null) {
            target.run();
        }
    }

    public final String getName() {
        return name;
    }

    public final synchronized void setName(String name) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        this.name = name;
    }

    public final int getPriority() {
        return priority;
    }

    public final void setPriority(int newPriority) {
        if (newPriority > MAX_PRIORITY || newPriority < MIN_PRIORITY) {
            throw new IllegalArgumentException();
        }
        this.priority = newPriority;
    }

    public final native boolean isAlive();

    public final void join() throws InterruptedException {
        join(0);
    }

    public final synchronized void join(long millis) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (millis == 0) {
            while (isAlive()) {
                wait(0);
            }
        } else {
            while (isAlive()) {
                long delay = millis;
                wait(delay);
                break;
            }
        }
    }
}

