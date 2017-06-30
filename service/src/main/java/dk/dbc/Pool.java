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

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author DBC {@literal <dbc.dk>}
 */
public class Pool<E> {

    private static final Logger log = LoggerFactory.getLogger(Pool.class);

    private static final Executor NULL_EXECUTOR = r -> r.run();

    public static class Element<E> implements AutoCloseable {

        private final E element;
        private final Pool<E> pool;
        boolean returned;

        private Element(E element, Pool<E> pool) {
            this.element = element;
            this.pool = pool;
            this.returned = false;
        }

        public E value() {
            return element;
        }

        @Override
        public void close() {
            if (returned) {
                return;
            }
            pool.returnElement(element);
            returned = true;
        }
    }

    private final BlockingDeque<E> elements = new LinkedBlockingDeque<>();
    private final ConcurrentHashMap<E, Long> expires = new ConcurrentHashMap<>();
    private final AtomicInteger size = new AtomicInteger(0);
    private final Supplier<E> supplier;
    private final Function<E, Boolean> validator;
    private final Executor executor;
    private final Integer max;
    private final int min;
    private final long ttl;

    private final Runnable runnable;

    private Pool(Supplier<E> supplier, Function<E, Boolean> validator, Executor executor, Integer max, int min, long ttl) {
        log.info("Creating");
        this.supplier = supplier;
        this.validator = validator;
        this.executor = executor;
        this.max = max;
        this.min = min;
        this.ttl = ttl;
        this.runnable = this::addElement;
        if (executor != NULL_EXECUTOR) {
            size.set(min);
            for (int i = 0 ; i < min ; i++) {
                this.executor.execute(runnable);
            }
        }
    }

    private void addElement() {
        log.trace("Adding element to pool");
        try {
            elements.addFirst(supplier.get());
        } catch (Exception e) {
            log.error("Error adding new element to pool: " + e.getMessage());
            log.debug("Error adding new element to pool:", e);
            size.decrementAndGet();
        }
    }

    public Element<E> take() throws InterruptedException {
        log.trace("take # size = " + size() + " " + elements.size());
        for (;;) {
            if (elements.size() <= min) {
                synchronized (this) {
                    long time = System.currentTimeMillis();
                    for (Iterator<Map.Entry<E, Long>> i = expires.entrySet().iterator() ; i.hasNext() ;) {
                        Map.Entry<E, Long> pair = i.next();
                        if (pair.getValue() <= time) {
                            i.remove();
                            size.decrementAndGet();
                            log.trace("Element expired: " + pair.getKey());
                        }
                    }
                    if (executor == NULL_EXECUTOR) {
                        if (max == null || max > size.get()) {
                            size.incrementAndGet();
                            executor.execute(runnable);
                        }
                    } else if (min == 0) {
                        if (max > size.get()) {
                            size.incrementAndGet();
                            executor.execute(runnable);
                        }
                    } else {
                        int construct = max == null ? min : Integer.min(max, min);
                        construct -= elements.size();
                        log.debug("contruct = " + construct);
                        for (int i = 0 ; i < construct && ( max == null || max > size() ) ; i++) {
                            size.incrementAndGet();
                            executor.execute(runnable);
                        }
                    }
                }
            }
            E element = elements.pollFirst(Long.max(1, ttl / 10), TimeUnit.MILLISECONDS);
            if (element != null) {
                if (validator.apply(element)) {
                    expires.put(element, System.currentTimeMillis() + ttl);
                    log.trace("Element offered: " + element);
                    return new Element<>(element, this);
                }
                log.trace("Element didn't validate: " + element);
            }
        }
    }

    public int size() {
        return size.get();
    }

    private void returnElement(E e) {
        if (expires.containsKey(e)) {
            expires.remove(e);
            elements.push(e);
            log.trace("Element returned " + e);
            if (max != null) {
                while (max < elements.size()) {
                    size.decrementAndGet();
                    E element = elements.pollLast();
                    if (element != null) {
                        log.trace("Removed extra element: " + element);
                    }
                }
            }
        } else {
            if (size() < max) {
                size.incrementAndGet();
                elements.addFirst(e);
                log.trace("Keepes expired element: " + e);
            } else {
                log.trace("Element returned but expired: " + e);
            }
        }
    }

    public static class Builder {

        private Executor executor = null;
        private Integer max = null;
        private Integer min = null;
        private Long ttl = null;

        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public Builder max(int max) {
            this.max = max;
            return this;
        }

        public Builder min(int min) {
            this.min = min;
            return this;
        }

        public Builder ttl(long duration, TimeUnit tu) {
            this.ttl = tu.toMillis(duration);
            return this;
        }

        public <E> Pool<E> build(Supplier<E> supplier) {
            return build(supplier, e -> true);
        }

        public <E> Pool<E> build(Supplier<E> supplier, Function<E, Boolean> validator) {
            return new Pool<>(supplier, validator, or(executor, NULL_EXECUTOR), max, or(min, 0), or(ttl, 10000L));
        }

        private static <E> E or(E left, E right) {
            return left != null ? left : right;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

}
