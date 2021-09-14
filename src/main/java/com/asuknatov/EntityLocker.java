package com.asuknatov;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;


public class EntityLocker<T> {
    private final Map<T, Stack<Thread>> threadLocks = new HashMap<>();

    public void lock(T key) throws InterruptedException {
        checkForNull(key);

        synchronized (threadLocks) {
            Stack<Thread> stack = getStack(key);
            for (;;) {
                if (stack.isEmpty()) {
                    stack.push(Thread.currentThread());
                    return;
                } else {
                    Thread owner = stack.peek();
                    if (owner == Thread.currentThread()) {
                        stack.push(owner);
                        return;
                    } else {
                        threadLocks.wait();
                    }
                }
            }
        }
    }

    public boolean tryLock(T key) {
        checkForNull(key);

        synchronized (threadLocks) {
            Stack<Thread> stack = getStack(key);
            if (stack.isEmpty()) {
                stack.push(Thread.currentThread());
                return true;
            } else {
                Thread owner = stack.peek();
                if (owner == Thread.currentThread()) {
                    stack.push(owner);
                    return true;
                }
                return false;
            }
        }
    }

    public boolean tryLock(T key, long timeout) throws InterruptedException {
        checkForNull(key);

        long deadline = System.nanoTime() + timeout;
        for (;;) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            timeout = deadline - System.nanoTime();
            if (timeout <= 0) {
                return false;
            }
            synchronized (threadLocks) {
                Stack<Thread> stack = getStack(key);
                if (stack.isEmpty()) {
                    stack.push(Thread.currentThread());
                    return true;
                } else {
                    Thread owner = stack.peek();
                    if (owner == Thread.currentThread()) {
                        stack.push(owner);
                        return true;
                    } else {
                        threadLocks.wait();
                    }
                }
            }
        }
    }

    public void unlock(T key) {
        checkForNull(key);

        synchronized (threadLocks) {
            Stack<Thread> stack = threadLocks.get(key);
            if (stack.isEmpty()) {
                return;
            }
            Thread owner = stack.peek();
            if (owner != Thread.currentThread()) {
                return;
            }

            stack.pop();
            threadLocks.notifyAll();
        }
    }

    public boolean isLocked(T key) {
        checkForNull(key);

        synchronized (threadLocks) {
            Stack<Thread> stack = threadLocks.get(key);
            return !stack.isEmpty();
        }
    }

    private Stack<Thread> getStack(T key) {
        return threadLocks.computeIfAbsent(key, k -> new Stack<>());
    }

    private void checkForNull(T key) {
        Objects.requireNonNull(key, "key cannot be null");
    }
}
