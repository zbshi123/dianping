package com.hmdp.utils;

/**
 *
 */
public interface ILock {
    /**
     * @param timeoutSec
     * @return
     */
    boolean tryLock(long timeoutSec);

    /**
     *
     */
    void unlock();
}
