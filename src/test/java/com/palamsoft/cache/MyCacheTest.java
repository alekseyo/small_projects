package com.palamsoft.cache;

import java.util.Random;

import org.junit.Test;

public class MyCacheTest {

	@Test
	public void test() {
		MyCache myCache = new MyCache(100, 100_000_000);
		
		Random random = new Random();
		for (int i = 0; i < 10000; i++) {
			byte[] b = new byte[random.nextInt(100000)];
			random.nextBytes(b);
			myCache.putToCache(b);
		}
	}

}
