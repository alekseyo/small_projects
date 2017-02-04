package com.palamsoft.cache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.log4j.Logger;

/**
 * 
 * @author Aleksey
 * 
 * Cache is backed by a list. Nodes are never removed. Each node is assigned an id that is equal to node's index.
 * 
 * The list is guarded by a read-write lock. The only write operation on the list itself is adding a node.
 * Readers can change state of the node (flush to disk, usage counter). So in addition to a read lock 
 * a reader synchronizes on node's monitor.
 * 
 * Maximum memory size validation is approximate.
 * 
 */
public class MyCache {
	
	private final Logger logger = Logger.getLogger(this.getClass());
	
	private static class Node {
		int usedCount;

		// If null then data is stored on disk
		byte[] value;
		boolean alreadyOnDisk = false;
	}

	// @GuardedBy("this")
	private final Map<String, byte[]> disk = new HashMap<String, byte[]>();

	// @GuardedBy("rwl")
	private final List<Node> cache;

	private final int maxMemorySizeBytes;

	// fair, so readers and writers get lock "in-order"
	private final ReadWriteLock rwl = new ReentrantReadWriteLock(true);

	public MyCache(int initialCapacity, int memorySizeBytes) {
		this.cache = new ArrayList<Node>(initialCapacity);
		this.maxMemorySizeBytes = memorySizeBytes;
	}
	public MyCache(int memorySizeBytes) {
		this.cache = new ArrayList<Node>();
		this.maxMemorySizeBytes = memorySizeBytes;
	}

	public int putToCache(byte[] data) {
		int newNodeId = 0;
		
		rwl.writeLock().lock();
		try {
			newNodeId = cache.size();
			Node newNode = new Node();
			newNode.value = Arrays.copyOf(data, data.length);
			cache.add(newNode);
		} finally {
			rwl.writeLock().unlock();
		}

		ensureMemoryThreshold(newNodeId);
		
		if (logger.isInfoEnabled()) {
			logger.info("Add node: " + newNodeId + ", size (bytes): " + data.length);
		}
		
		return newNodeId;
	}

	public byte[] getFromCache(int id) {
		byte[] result;

		boolean needRevalidateCache = false;
		rwl.readLock().lock();
		try {

			if (id >= cache.size() || id < 0) {
				logger.warn("Invalid id: " + id);
				return null;
			}

			Node node = cache.get(id);
			synchronized (node) {

				if (node.value == null) {
					if (logger.isInfoEnabled()) {
						logger.info("Read from disk node id: " + id);
					}
					node.value = loadFromFile(new Integer(id).toString());
					needRevalidateCache = true;
				}
				node.usedCount++;
				result = Arrays.copyOf(node.value, node.value.length);
			}
		} finally {
			rwl.readLock().unlock();
		}

		if (needRevalidateCache) {
			ensureMemoryThreshold(id);
		}
		return result;
	}

	/**
	 * if {@link #maxMemorySizeBytes} exceeded then flushes least used node (but not
	 * {@code idNotToCount}) to disk until memory fits the limit
	 * 
	 * @param idNotToCount
	 */
	private void ensureMemoryThreshold(int idNotToCount) {
		logger.info("ensureMemoryThreshold");
		rwl.readLock().lock();
		try {
			while (true) {
				int totalSize = 0;
				int minUsed = -1;
				Node leastUsed = null;
				int leastUsedId = 0;

				for (int i = 0; i < cache.size(); i++) {
					Node node = cache.get(i);
					synchronized (node) {
						if (node.value == null) {
							continue;
						}
						totalSize += node.value.length;
						if (i == idNotToCount) {
							continue;
						}
						if (leastUsed == null || minUsed > node.usedCount) {
							minUsed = node.usedCount;
							leastUsed = node;
							leastUsedId = i;
						}
					}
				}
				
				if (logger.isDebugEnabled()) {
					logger.debug("totalSize: " + totalSize);
				}

				if (totalSize > maxMemorySizeBytes) {
					synchronized (leastUsed) {
						if (!leastUsed.alreadyOnDisk) {
							if (logger.isInfoEnabled()) {
								logger.info("Flush to disk node id: " + leastUsedId 
												+ ", size (bytes): " + leastUsed.value.length);
							}
							saveToFile(new Integer(leastUsedId).toString(), leastUsed.value);
						}
						totalSize -= leastUsed.value.length;
						leastUsed.value = null;
						
					}
				}
				if (totalSize <= maxMemorySizeBytes) {
					break;
				}
			}
		} finally {
			rwl.readLock().unlock();
		}
	}

	public synchronized byte[] loadFromFile(String filename) {

		return disk.get(filename);
	}

	public synchronized void saveToFile(String filename, byte[] data) {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			throw new RuntimeException("Failed to save file", e);
		}

		disk.put(filename, data);

	}

}
