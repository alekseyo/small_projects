package com.palamsoft.cache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.log4j.Logger;

import net.jcip.annotations.GuardedBy;

/**
 * 
 * Cache is backed by a ArrayList. Nodes are never removed. Each node is assigned an id that is equal to node's index.
 * 
 * The list is guarded by a read-write lock. The only write operation on the list itself is adding a node.
 * Readers can change state of the node itself (flush to disk, usage counter). So in addition to a read lock 
 * a reader synchronizes on node's monitor.
 * 
 * Memory usage is controlled by a daemon thread which stores least used entries on disk. Cache signals this
 * thread that memory limit is reached, the thread frees memory until lowMemoryLimit.
 * 
 */
public class MyCache {
	
	private final Logger logger = Logger.getLogger(this.getClass());
	
	private static class Node {
		private int usedCount;

		// If value == null then data is stored on disk
		private byte[] value;
		private final int size;
		private boolean alreadyOnDisk = false;
		
		public Node(byte[] value) {
			this.value = value;
			this.size = value.length;
		}
	}
	
	@GuardedBy("this")
	private final Map<String, byte[]> disk = new HashMap<String, byte[]>();

	@GuardedBy("rwl")
	private final List<Node> cache;
	
	@GuardedBy("rwl")
	private long memorySize = 0; 

	private final long highMemoryLimit;
	private final long lowMemoryLimit;
	private final Thread cleanupThread;

	// fair, so readers and writers get lock "in-order"
	private final ReadWriteLock rwl = new ReentrantReadWriteLock(true);
	
	// CONDITION PREDICATE: noMemory (memorySize + loaded_node_size > highMemoryLimit)
	private final Condition noMemory = rwl.writeLock().newCondition();
	
	public MyCache(int initialCapacity, long highLimitBytes, long lowLimitBytes) {
		this.cache = (initialCapacity == 0) ? new ArrayList<>() 
				: new ArrayList<Node>(initialCapacity);
		this.highMemoryLimit = highLimitBytes;
		this.lowMemoryLimit = lowLimitBytes;
		cleanupThread = new Thread( new Cleaner() );
		cleanupThread.setDaemon(true);
	}
		
	public MyCache(long highLimitBytes) {
		this(0, highLimitBytes, (int) ( highLimitBytes * 0.8 ) );
	}

	// BLOCKS-UNTIL: memoryAvailable
	public int putToCache(byte[] data) {
		int id = 0;

		rwl.writeLock().lock();

		if (cleanupThread.getState() == Thread.State.NEW) {
			cleanupThread.start();
		}

		try {
			if (data.length + memorySize > highMemoryLimit) {
				noMemory.signalAll();
				throw new CacheUnavailableException("No room for new node. Please retry later");
			}
			Node node = new Node(Arrays.copyOf(data, data.length));
			id = cache.size();
			cache.add(node);
			memorySize += node.size;
		} finally {
			rwl.writeLock().unlock();
		}

		if (logger.isInfoEnabled()) {
			logger.info("Added node: " + id + ", size (bytes): " + data.length);
		}

		return id;
	}
	
	// BLOCKS-UNTIL: memoryAvailable
	public byte[] getFromCache(int id) {
		Node node;

		rwl.readLock().lock();
		try {
			if (id >= cache.size() || id < 0) {
				logger.warn("Invalid id: " + id);
				return null;
			}
			node = cache.get(id);
		} finally {
			rwl.readLock().unlock();
		}

		byte[] result;
		synchronized (node) {
			node.usedCount++;
			if (node.value == null) {
				if (logger.isInfoEnabled()) {
					logger.info("Read from disk node id: " + id);
				}

				byte[] loaded = loadFromFile(new Integer(id).toString());
				rwl.writeLock().lock();
				try {
					if (node.size + memorySize > highMemoryLimit) {
						if (logger.isDebugEnabled()) {
							logger.debug("Not enough memory to load node id: " + id);
						}
						noMemory.signalAll();
						return loaded;
					}
					else {
						node.value = loaded; // should not escape
						memorySize += node.size;
					}
				} finally {
					rwl.writeLock().unlock();
				}
			}
			result = Arrays.copyOf(node.value, node.value.length);
		}
		if (logger.isInfoEnabled()) {
			logger.info("Return value of node id: " + id);
		}
		return result;
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
	
	private class Cleaner implements Runnable {

		// BLOCKS-UNTIL: noMemory
		@Override
		public void run() {
			while (true) {
				rwl.writeLock().lock();
				try {
					noMemory.awaitUninterruptibly();
				} finally {
					rwl.writeLock().unlock();
				}

				freeMemory();
			}
		}

		private void freeMemory() {
			PriorityQueue<NodeSnapshot> queue;
			rwl.readLock().lock();
			try {
				queue = new PriorityQueue<>(cache.size());

				logger.debug("Gather statistics");
				for (int i = 0; i < cache.size(); i++) {
					Node node = cache.get(i);
					synchronized (node) {
						if (node.value == null) {
							continue;
						}
						queue.add(new NodeSnapshot(node, i, node.usedCount));
					}
				}
			} finally {
				rwl.readLock().unlock();
			}

			logger.debug("Clean up unused cache items");
			boolean cleanedEnough = false;
			for (NodeSnapshot nodeSnapshot = queue.poll(); nodeSnapshot != null; nodeSnapshot = queue.poll()) {
				Node node = nodeSnapshot.node;
				synchronized (node) {
					if (node.value == null) {
						continue;
					}

					if (!node.alreadyOnDisk) {
						if (logger.isInfoEnabled()) {
							logger.info("Flush to disk node id: " + nodeSnapshot.id + ", size (bytes): " + node.size);
						}
						saveToFile(new Integer(nodeSnapshot.id).toString(), node.value);
					}
					node.value = null;
				}

				rwl.writeLock().lock();
				try {
					memorySize -= node.size;
					if (memorySize <= lowMemoryLimit) {
						cleanedEnough = true;
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Memory used: " + memorySize);
					}
				} finally {
					rwl.writeLock().unlock();
				}
				if (cleanedEnough) {
					break;
				}
			}
		}

		private class NodeSnapshot implements Comparable<NodeSnapshot> {
			Node node;
			int usedCount;
			int id;

			public NodeSnapshot(Node node, int id, int usedCount) {
				this.node = node;
				this.id = id;
				this.usedCount = usedCount;
			}

			@Override
			public int compareTo(NodeSnapshot o) {
				return Integer.compare(usedCount, o.usedCount);
			}
		}
	}
	
	public static class CacheUnavailableException extends RuntimeException {

		public CacheUnavailableException(String message) {
			super(message);
		}

	}

}
