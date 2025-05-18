package com.bank.vwap;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CurrencyData {
    // Fields are grouped for better cache locality
    
    // Frequently accessed calculation fields
    private volatile double vwap;
    private final DoubleAdder totalWeightedPrice;
    private final AtomicLong totalVolume;
    
    // Collection and synchronization
    private final Deque<CurrencyPriceData> priceStream;
    private final ReadWriteLock lock;
    
    // Cache line padding to prevent false sharing
    private long p1, p2, p3, p4, p5, p6, p7;
    
    // Initial capacity for ArrayDeque to reduce resizing
    private static final int INITIAL_CAPACITY = 1024;

    public CurrencyData() {
        // Use ArrayDeque for better memory locality and cache performance
        this.priceStream = new ArrayDeque<>(INITIAL_CAPACITY);
        this.totalWeightedPrice = new DoubleAdder();
        this.totalVolume = new AtomicLong(0);
        this.vwap = 0.0;
        this.lock = new ReentrantReadWriteLock();
    }

    public double getVwap() {
        return vwap;
    }

    public void setVwap(double vwap) {
        this.vwap = vwap;
    }

    /**
     * Thread-safe access to the price stream.
     * For compatibility with existing code, but returns a copy for safety.
     */
    public Deque<CurrencyPriceData> getPriceStream() {
        // Acquire read lock to safely read the collection
        lock.readLock().lock();
        try {
            // Return a copy to avoid external modification without locks
            return new ArrayDeque<>(priceStream);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Add an item to the front of the deque in a thread-safe manner
     * 
     * @param data The price data to add
     */
    public void addToFront(CurrencyPriceData data) {
        lock.writeLock().lock();
        try {
            priceStream.addFirst(data);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove items from the end of the deque that are before the cutoff time
     *
     * @param cutoffTime The time before which items should be removed
     * @return true if any items were removed
     */
    public boolean removeItemsBeforeCutoff(Instant cutoffTime) {
        boolean removedAny = false;
        
        lock.writeLock().lock();
        try {
            while (!priceStream.isEmpty()) {
                CurrencyPriceData last = priceStream.getLast();
                if (last.getTimestamp().isBefore(cutoffTime)) {
                    priceStream.removeLast();
                    removedAny = true;
                } else {
                    break; // Stop once we hit an item within the cutoff
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
        
        return removedAny;
    }

    /**
     * Thread-safe check if the price stream is empty
     * 
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return priceStream.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get an iterator for the items in reverse order (oldest to newest)
     * This method is for compatibility with the existing VWAPCalculator
     */
    public Iterator<CurrencyPriceData> getDescendingIterator() {
        lock.readLock().lock();
        try {
            // Create a copy with items in reverse order
            ArrayDeque<CurrencyPriceData> copy = new ArrayDeque<>(priceStream.size());
            for (CurrencyPriceData item : priceStream) {
                copy.addFirst(item);
            }
            // Release the lock before returning the iterator
            lock.readLock().unlock();
            return copy.iterator();
        } catch (Exception e) {
            lock.readLock().unlock();
            throw e;
        }
    }

    public DoubleAdder getTotalWeightedPrice() {
        return totalWeightedPrice;
    }

    public AtomicLong getTotalVolume() {
        return totalVolume;
    }
    
    // Cache line padding to prevent false sharing
    private long q1, q2, q3, q4, q5, q6, q7;
}