package com.asuknatov;

import org.junit.Test;

import java.util.concurrent.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestEntityLocker {

    @Test(timeout = 1000)
    public void testTryLockReentrancy() {
        String id = "testId";
        EntityLocker<String> locker = new EntityLocker<>();

        try {
            locker.tryLock(id);
            assertTrue(locker.isLocked(id));

            locker.tryLock(id);
            assertTrue(locker.isLocked(id));
        } finally {
            locker.unlock(id);
            assertTrue(locker.isLocked(id));

            locker.unlock(id);
            assertFalse(locker.isLocked(id));
        }
    }

    @Test(timeout = 1000)
    public void testLockReentrancy() throws InterruptedException {
        String id = "testId";
        EntityLocker<String> locker = new EntityLocker<>();

        try {
            locker.lock(id);
            assertTrue(locker.isLocked(id));

            locker.lock(id);
            assertTrue(locker.isLocked(id));
        } finally {
            locker.unlock(id);
            assertTrue(locker.isLocked(id));

            locker.unlock(id);
            assertFalse(locker.isLocked(id));
        }
    }

    @Test(timeout = 1000)
    public void testUnlock() throws InterruptedException {
        String id = "testId";
        EntityLocker<String> locker = new EntityLocker<>();

        Thread t = new Thread(() -> {
            try {
                locker.lock(id);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                locker.unlock(id);
            }
        }, "unlockTest");

        try {
            locker.lock(id);
            t.start();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } finally {
            locker.unlock(id);
            t.join();
            assertFalse(locker.isLocked(id));
        }
    }

    @Test(timeout = 1000)
    public void testSameIdLockOnMultipleThreads() throws ExecutionException, InterruptedException {
        String id1 = "testId1";
        EntityLocker<String> locker = new EntityLocker<>();

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            long timeoutNanos = TimeUnit.MICROSECONDS.toNanos(100);
            Callable<Boolean> lockCall = new LockCall(locker, id1, timeoutNanos);
            Future<Boolean> call1 = executorService.submit(lockCall);
            Future<Boolean> call2 = executorService.submit(lockCall);

            assertTrue(call1.get() ^ call2.get());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test(timeout = 1000)
    public void testVariousIdsLockOnMultipleThreads() throws ExecutionException, InterruptedException {
        String id1 = "testId1";
        String id2 = "testId2";
        EntityLocker<String> locker = new EntityLocker<>();

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> call1 = executorService.submit(new LockCall(locker, id1, -1));
            Future<Boolean> call2 = executorService.submit(new LockCall(locker, id2, -1));

            assertTrue(call1.get());
            assertTrue(call2.get());
        } finally {
            executorService.shutdownNow();
        }
    }


    private static class LockCall implements Callable<Boolean> {
        private final EntityLocker<String> locker;
        private final String id;
        private final long timeoutNanos;

        public LockCall(EntityLocker<String> locker, String id, long timeoutNanos) {
            this.locker = locker;
            this.id = id;
            this.timeoutNanos = timeoutNanos;
        }

        @Override
        public Boolean call() throws Exception {
            try {
                boolean success = timeoutNanos > 0 ? locker.tryLock(id, timeoutNanos) : locker.tryLock(id);
                Thread.sleep(100);
                return success;
            } finally {
                locker.unlock(id);
            }
        }
    }
}
