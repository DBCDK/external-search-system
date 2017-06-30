/*
 * Copyright (C) 2017 DBC A/S (http://dbc.dk/)
 *
 * This is part of dbc-ess-service
 *
 * dbc-ess-service is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * dbc-ess-service is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dbc;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

/**
 *
 * @author DBC {@literal <dbc.dk>}
 */
@Ignore
public class PoolTest {

    private static final Logger log = LoggerFactory.getLogger(PoolTest.class);

    public PoolTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testNoExecutor() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        AtomicInteger i = new AtomicInteger();

        Pool<Integer> pool = Pool.builder()
                .max(2)
                .ttl(250, TimeUnit.MILLISECONDS)
                .build(() -> {
                    try {
                        Thread.sleep(100);
                        return i.incrementAndGet();
                    } catch (InterruptedException ex) {
                        log.error("Error sleeping in supplier: " + ex.getMessage());
                        log.debug("Error sleeping in supplier:", ex);
                        throw new RuntimeException(ex);
                    }
                });
        long ts = System.currentTimeMillis();
        try (Pool.Element<Integer> one = pool.take()) {
            long oneTs = System.currentTimeMillis();
            try (Pool.Element<Integer> two = pool.take()) {
                long twoTs = System.currentTimeMillis();
                try (Pool.Element<Integer> three = pool.take()) {
                    long threeTs = System.currentTimeMillis();
                    try (Pool.Element<Integer> four = pool.take()) {
                        long fourTs = System.currentTimeMillis();
                        try (Pool.Element<Integer> five = pool.take()) {
                            long fiveTs = System.currentTimeMillis();

                            assertEquals(1, (long) one.value());
                            assertEquals(2, (long) two.value());
                            assertEquals(3, (long) three.value());
                            assertEquals(4, (long) four.value());
                            assertEquals(5, (long) five.value());

                            long oneDelay = oneTs - ts;
                            log.debug("oneDelay = " + oneDelay);
                            assertThat("timeout One", oneDelay, greaterThanOrEqualTo(75L));
                            assertThat("timeout One", oneDelay, lessThanOrEqualTo(150L));

                            long twoDelay = twoTs - oneTs;
                            log.debug("twoDelay = " + twoDelay);
                            assertThat("timeout Two", twoDelay, greaterThanOrEqualTo(75L));
                            assertThat("timeout Two", twoDelay, lessThanOrEqualTo(150L));

                            long threeDelay = threeTs - twoTs;
                            log.debug("threeDelay = " + threeDelay);
                            assertThat("timeout Three", threeDelay, greaterThanOrEqualTo(250L));
                            assertThat("timeout Three", threeDelay, lessThanOrEqualTo(500L));

                            long fourDelay = fourTs - threeTs;
                            log.debug("fourDelay = " + fourDelay);
                            assertThat("timeout Four", fourDelay, greaterThanOrEqualTo(75L));
                            assertThat("timeout Four", fourDelay, lessThanOrEqualTo(150L));

                            long fiveDelay = fiveTs - fourTs;
                            log.debug("fiveDelay = " + fiveDelay);
                            assertThat("timeout Five", fiveDelay, greaterThanOrEqualTo(250L));
                            assertThat("timeout Five", fiveDelay, lessThanOrEqualTo(500L));

                        }
                    }
                }
            }
        }
        ts = System.currentTimeMillis();
        try (Pool.Element<Integer> one = pool.take()) {
            long oneTs = System.currentTimeMillis();
            try (Pool.Element<Integer> two = pool.take()) {
                long twoTs = System.currentTimeMillis();

                assertEquals(4, (long) one.value());
                assertEquals(5, (long) two.value());

                long oneDelay = oneTs - ts;
                log.debug("oneDelay = " + oneDelay);
                assertThat("timeout One", oneDelay, lessThanOrEqualTo(10L));

                long twoDelay = twoTs - oneTs;
                log.debug("twoDelay = " + twoDelay);
                assertThat("timeout Two", twoDelay, lessThanOrEqualTo(10L));
            }
        }

    }

    @Test
    public void testExecutor() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        AtomicInteger i = new AtomicInteger();

        Pool<Integer> pool = Pool.builder()
                .executor(executor)
                .max(2)
                .ttl(250, TimeUnit.MILLISECONDS)
                .build(() -> {
                    try {
                        log.info("pre");
                        Thread.sleep(100);
                        log.info("post");
                        return i.incrementAndGet();
                    } catch (InterruptedException ex) {
                        log.error("Error sleeping in supplier: " + ex.getMessage());
                        log.debug("Error sleeping in supplier:", ex);
                        throw new RuntimeException(ex);
                    }
                });
        long ts = System.currentTimeMillis();
        try (Pool.Element<Integer> one = pool.take()) {
            long oneTs = System.currentTimeMillis();
            try (Pool.Element<Integer> two = pool.take()) {
                long twoTs = System.currentTimeMillis();
                try (Pool.Element<Integer> three = pool.take()) {
                    long threeTs = System.currentTimeMillis();
                    try (Pool.Element<Integer> four = pool.take()) {
                        long fourTs = System.currentTimeMillis();
                        try (Pool.Element<Integer> five = pool.take()) {
                            long fiveTs = System.currentTimeMillis();

                            assertEquals(1, (long) one.value());
                            assertEquals(2, (long) two.value());
                            assertEquals(3, (long) three.value());
                            assertEquals(4, (long) four.value());
                            assertEquals(5, (long) five.value());

                            long oneDelay = oneTs - ts;
                            log.debug("oneDelay = " + oneDelay);
                            assertThat("timeout One", oneDelay, greaterThanOrEqualTo(50L));
                            assertThat("timeout One", oneDelay, lessThanOrEqualTo(175L));

                            // Done in parallel
                            long twoDelay = twoTs - oneTs;
                            log.debug("twoDelay = " + twoDelay);
                            assertThat("timeout Two", twoDelay, greaterThanOrEqualTo(0L));
                            assertThat("timeout Two", twoDelay, lessThanOrEqualTo(50L));

                            long threeDelay = threeTs - twoTs;
                            log.debug("threeDelay = " + threeDelay);
                            assertThat("timeout Three", threeDelay, greaterThanOrEqualTo(250L));
                            assertThat("timeout Three", threeDelay, lessThanOrEqualTo(500L));

                            long fourDelay = fourTs - threeTs;
                            log.debug("fourDelay = " + fourDelay);
                            assertThat("timeout Four", fourDelay, greaterThanOrEqualTo(0L));
                            assertThat("timeout Four", fourDelay, lessThanOrEqualTo(50L));

                            long fiveDelay = fiveTs - fourTs;
                            log.debug("fiveDelay = " + fiveDelay);
                            assertThat("timeout Five", fiveDelay, greaterThanOrEqualTo(250L));
                            assertThat("timeout Five", fiveDelay, lessThanOrEqualTo(500L));

                        }
                    }
                }
            }
        }
        ts = System.currentTimeMillis();
        try (Pool.Element<Integer> one = pool.take()) {
            long oneTs = System.currentTimeMillis();
            try (Pool.Element<Integer> two = pool.take()) {
                long twoTs = System.currentTimeMillis();

//                assertEquals(5, (long) one.value());
//                assertEquals(4, (long) two.value());

                long oneDelay = oneTs - ts;
                log.debug("oneDelay = " + oneDelay);
                assertThat("timeout One", oneDelay, lessThanOrEqualTo(10L));

                long twoDelay = twoTs - oneTs;
                log.debug("twoDelay = " + twoDelay);
                assertThat("timeout Two", twoDelay, lessThanOrEqualTo(10L));
            }
        }

    }
}
