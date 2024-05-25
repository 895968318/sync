package com.jd.springboot2;

import lombok.extern.slf4j.Slf4j;
import org.caffinitas.ohc.OHCache;
import org.caffinitas.ohc.OHCacheBuilder;
import org.caffinitas.ohc.alloc.UnsafeAllocator;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

@SpringBootApplication
@RestController
@Slf4j
public class SpringBoot2Application {

    public static int MB = 1024 * 1024;

    public static OHCache<String, byte[]> ohCache = OHCacheBuilder.<String, String>
                    newBuilder()
            .keySerializer(new KryoSerializer())
            .valueSerializer(new KryoSerializer())
            .capacity(1024 * 1024 * 200)
            .segmentCount(1)
            .maxEntrySize(1024 * 1024 * 100)
            .timeouts(true)
            .defaultTTLmillis(1000 * 60 * 60).build();

    static final Unsafe unsafe;
    static ByteBuffer directBuffer = ByteBuffer.allocateDirect(0);
    static long DIRECT_BYTE_BUFFER_ADDRESS_OFFSET;
    static long DIRECT_BYTE_BUFFER_CAPACITY_OFFSET;
    static long DIRECT_BYTE_BUFFER_LIMIT_OFFSET;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
            DIRECT_BYTE_BUFFER_ADDRESS_OFFSET = unsafe.objectFieldOffset(Buffer.class.getDeclaredField("address"));
            DIRECT_BYTE_BUFFER_CAPACITY_OFFSET = unsafe.objectFieldOffset(Buffer.class.getDeclaredField("capacity"));
            DIRECT_BYTE_BUFFER_LIMIT_OFFSET = unsafe.objectFieldOffset(Buffer.class.getDeclaredField("limit"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void main(String[] args) throws InstantiationException {
        //SpringApplication.run(SpringBoot2Application.class, args);
        Class<? extends ByteBuffer> aClass = directBuffer.getClass();
        long len = 1024L * 1024 * 300;
        long allocate = unsafe.allocateMemory(len);

        ByteBuffer bb = (ByteBuffer) unsafe.allocateInstance(aClass);
        unsafe.putLong(bb, DIRECT_BYTE_BUFFER_ADDRESS_OFFSET, allocate);
        unsafe.putInt(bb, DIRECT_BYTE_BUFFER_CAPACITY_OFFSET, (int) len);
        unsafe.putInt(bb, DIRECT_BYTE_BUFFER_LIMIT_OFFSET, (int) len);
        bb.order(ByteOrder.BIG_ENDIAN);


        for (long i = 0; i < len; i++) {
            bb.put((byte) 12);
        }
    }


    @GetMapping("hello")
    public String hello() {
        return "hello";
    }

    @GetMapping("put")
    public String allocate(String key, int size) {
        byte[] bytes = new byte[size * MB];
        Arrays.fill(bytes, (byte) 12);
        boolean put = ohCache.put(key, bytes);
        return put ? "success" : "fail";
    }

    @GetMapping("get")
    public String allocate(String key) {
        byte[] cacheValue = ohCache.get(key);
        log.info("get cache value:{}", cacheValue);
        return "success";
    }
}
