package com.palamsoft.cache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
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
		private final int id;

		private int usedCount;

		// If null then data is stored on disk
		private byte[] value;
		private boolean alreadyOnDisk = false;
		
		public Node(int id) {
			this.id = id;
		}
	}

	// @GuardedBy("this")
	private final Map<String, byte[]> disk = new HashMap<String, byte[]>();

	// @GuardedBy("rwl")
	private final List<Node> cache;
	
	private final AtomicLong memorySize = new AtomicLong(); 

	private final long highMemoryLimit;
	private final long lowMemoryLimit;

	// fair, so readers and writers get lock "in-order"
	private final ReadWriteLock rwl = new ReentrantReadWriteLock(true);

	public MyCache(int initialCapacity, long highLimitBytes, long lowLimitBytes) {
		this.cache = (initialCapacity == 0) ? new ArrayList<>() 
				: new ArrayList<Node>(initialCapacity);
		this.highMemoryLimit = highLimitBytes;
		this.lowMemoryLimit = lowLimitBytes;
	}
	public MyCache(long highLimitBytes) {
		this(0, highLimitBytes, (int) ( highLimitBytes * 0.8 ) );
	}

	public int putToCache(byte[] data) {
		int newNodeId = 0;
		
		rwl.writeLock().lock();
		try {
			newNodeId = cache.size();
			Node newNode = new Node(newNodeId);
			newNode.value = Arrays.copyOf(data, data.length);
			cache.add(newNode);
			memorySize.addAndGet(newNode.value.length);
		} finally {
			rwl.writeLock().unlock();
		}

		ensureMemoryThreshold();
		
		if (logger.isInfoEnabled()) {
			logger.info("Add node: " + newNodeId + ", size (bytes): " + data.length);
		}
		
		return newNodeId;
	}

	public byte[] getFromCache(int id) {
		byte[] result;

		boolean memoryExpanded = false;
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
					memorySize.addAndGet(node.value.length);
					memoryExpanded = true;
				}
				node.usedCount++;
				result = Arrays.copyOf(node.value, node.value.length);
			}
		} finally {
			rwl.readLock().unlock();
		}

		if (memoryExpanded) {
			ensureMemoryThreshold();
		}
		return result;
	}

	/**
	 * if {@link #highMemoryLimit} exceeded then flushes least used node 
	 *  to disk until memory fits {@link #lowMemoryLimit}
	 */
	private void ensureMemoryThreshold() {
		if (memorySize.get() < highMemoryLimit) {
			if (logger.isInfoEnabled()) {
				logger.info("Memory size: " + memorySize.get() + " OK");
			}
			return;
		}
		logger.info("ensureMemoryThreshold");

		rwl.readLock().lock();
		try {
			PriorityQueue<NodeSnapshot> queue = new PriorityQueue<>(cache.size());

			logger.debug("Gather statistics");
			for (int i = 0; i < cache.size(); i++) {
				Node node = cache.get(i);
				synchronized (node) {
					if (node.value == null) {
						continue;
					}
					queue.add(new NodeSnapshot(node, node.usedCount));
				}
			}

			logger.debug("Clean up unused cache items");
			for (NodeSnapshot nodeSnapshot = queue.poll(); nodeSnapshot != null; nodeSnapshot = queue.poll()) {
				Node node = nodeSnapshot.node;
				synchronized (node) {
					if (node.value == null) {
						continue;
					}

					if (!node.alreadyOnDisk) {
						if (logger.isInfoEnabled()) {
							logger.info("Flush to disk node id: " + node.id + ", size (bytes): " + node.value.length);
						}
						saveToFile(new Integer(node.id).toString(), node.value);
					}
					memorySize.addAndGet(-node.value.length);
					node.value = null;
				}
				if (memorySize.get() <= lowMemoryLimit) {
					break;
				}
			}
		} finally {
			rwl.readLock().unlock();
		}
	}
	
	private static class NodeSnapshot implements Comparable<NodeSnapshot> {
		Node node;
		int usedCount;

		public NodeSnapshot(Node node, int size) {
			super();
			this.node = node;
			this.usedCount = size;
		}

		@Override
		public int compareTo(NodeSnapshot o) {
			return Integer.compare(usedCount, o.usedCount);
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
