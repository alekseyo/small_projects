package com.palamsoft.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Assert;
import org.junit.Test;

import com.palamsoft.cache.MyCache.CacheUnavailableException;

public class MyCacheTest {
	private static final int THREADS_COUNT = 10;
	private static CyclicBarrier barrier = new CyclicBarrier(THREADS_COUNT + 1);

	@Test
	public void test() {
		MyCache myCache = new MyCache(100, 100_000_000, 80_000_000);

		ExecutorService executor = Executors.newFixedThreadPool(THREADS_COUNT);
		for (int i = 0; i < THREADS_COUNT; i++) {
			executor.submit(new Reader(myCache));
		}
		try {
			barrier.await();
			barrier.await();
		} catch (InterruptedException | BrokenBarrierException e) {
			throw new RuntimeException("failed to wait barrier", e);
		}

	}

	private static class Reader implements Runnable {

		private final MyCache cache;

		public Reader(MyCache cache) {
			this.cache = cache;
		}

		@Override
		public void run() {
			try {
				barrier.await();
				final int OBJECTS_COUNT = 1000;
				final int READS_COUNT = 10000;
				final int MAX_OBJECT_SIZE = 100_000;
				Map<Integer, byte[]> data = new HashMap<>(OBJECTS_COUNT);

				int countWrote = 0, countRead = 0;
				Random random = new Random();

				while (true) {
					if (countWrote >= OBJECTS_COUNT && countRead >= READS_COUNT) {
						break;
					}

					if (random.nextInt(100) < 10) {
						if (countWrote >= OBJECTS_COUNT) {
							continue;
						}
						byte[] b = new byte[random.nextInt(MAX_OBJECT_SIZE)];
						random.nextBytes(b);
						try {
							data.put(cache.putToCache(b), b);
							countWrote++;
						}
						catch(CacheUnavailableException e) {
							System.out.println("Cannot add new node");
						}
					} else {
						if (countWrote == 0 || countRead >= READS_COUNT) {
							continue;
						}

						List<Integer> keys = new ArrayList<>(data.keySet());
						int id = keys.get(random.nextInt(keys.size()));
						try {
							Assert.assertArrayEquals(data.get(id), cache.getFromCache(id));
							countRead++;
						}
						catch (CacheUnavailableException e) {
							System.out.println("Unable to read node id: " + id);
						}
					}
					
					Thread.sleep(random.nextInt(10));

				}

				barrier.await();
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}

	}

}
