package com.aih.highlike.manager.cache;

import cn.hutool.core.util.HashUtil;
import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.util.DigestUtils;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * HeavyKeeper 算法实现
 * <p>
 * 用于高效检测数据流中的 TopK 热点元素
 * <p>
 * 核心特点：
 * 1. 使用二维数组存储计数信息
 * 2. 通过概率衰减机制处理哈希冲突
 * 3. 维护最小堆保存 TopK 元素
 */
public class HeavyKeeper implements TopK {
    
    /**
     * 衰减查找表大小
     */
    private static final int LOOKUP_TABLE_SIZE = 256;
    
    /**
     * TopK 的 K 值
     */
    private final int k;
    
    /**
     * 桶数组宽度
     */
    private final int width;
    
    /**
     * 桶数组深度
     */
    private final int depth;
    
    /**
     * 衰减概率查找表
     */
    private final double[] lookupTable;
    
    /**
     * 二维桶数组
     */
    private final Bucket[][] buckets;
    
    /**
     * 最小堆，维护 TopK
     */
    private final PriorityQueue<Node> minHeap;
    
    /**
     * 被挤出的元素队列
     */
    private final BlockingQueue<Item> expelledQueue;
    
    /**
     * 随机数生成器
     */
    private final Random random;
    
    /**
     * 总访问次数
     */
    private long total;
    
    /**
     * 最小计数阈值
     */
    private final int minCount;
    
    /**
     * 构造函数
     *
     * @param k        TopK 的 K 值
     * @param width    桶数组宽度
     * @param depth    桶数组深度
     * @param decay    衰减系数
     * @param minCount 最小计数阈值
     */
    public HeavyKeeper(int k, int width, int depth, double decay, int minCount) {
        this.k = k;
        this.width = width;
        this.depth = depth;
        this.minCount = minCount;
        
        // 初始化衰减查找表
        this.lookupTable = new double[LOOKUP_TABLE_SIZE];
        for (int i = 0; i < LOOKUP_TABLE_SIZE; i++) {
            lookupTable[i] = Math.pow(decay, i);
        }
        
        // 初始化桶数组
        this.buckets = new Bucket[depth][width];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                buckets[i][j] = new Bucket();
            }
        }
        
        // 初始化最小堆
        this.minHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count));
        this.expelledQueue = new LinkedBlockingQueue<>();
        this.random = new Random();
        this.total = 0;
    }

    /**
     *  HeavyKeeper 算‌法的核心，添加元素并更新TopK结构
     *
     * @param key       键
     * @param increment 增量
     * @return 添加结果
     */
    @Override
    public AddResult add(String key, int increment) {
        byte[] keyBytes = key.getBytes();
        long itemFingerprint = hash(keyBytes);
        int maxCount = 0;
        
        // 更新所有深度的桶
        for (int i = 0; i < depth; i++) {
            int bucketNumber = Math.abs(hash(keyBytes, i)) % width;
            Bucket bucket = buckets[i][bucketNumber];
            
            synchronized (bucket) {
                if (bucket.count == 0) {
                    // 桶为空，直接设置
                    bucket.fingerprint = itemFingerprint;
                    bucket.count = increment;
                    maxCount = Math.max(maxCount, increment);
                } else if (bucket.fingerprint == itemFingerprint) {
                    // 指纹匹配，增加计数
                    bucket.count += increment;
                    maxCount = Math.max(maxCount, bucket.count);
                } else {
                    // 哈希冲突，概率衰减
                    for (int j = 0; j < increment; j++) {
                        double decayProb = bucket.count < LOOKUP_TABLE_SIZE ?
                                lookupTable[bucket.count] :
                                lookupTable[LOOKUP_TABLE_SIZE - 1];
                        
                        if (random.nextDouble() < decayProb) {
                            bucket.count--;
                            if (bucket.count == 0) {
                                bucket.fingerprint = itemFingerprint;
                                bucket.count = increment - j;
                                maxCount = Math.max(maxCount, bucket.count);
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        total += increment;
        
        // 如果计数小于阈值，不加入 TopK
        if (maxCount < minCount) {
            return new AddResult(null, false, null);
        }
        
        // 更新 TopK
        synchronized (minHeap) {
            boolean isHot = false;
            String expelled = null;
            
            // 检查是否已在堆中
            Optional<Node> existing = minHeap.stream()
                    .filter(n -> n.key.equals(key))
                    .findFirst();
            
            if (existing.isPresent()) {
                // 已存在，更新计数
                minHeap.remove(existing.get());
                minHeap.add(new Node(key, maxCount));
                isHot = true;
            } else {
                // 不存在，判断是否应该加入
                if (minHeap.size() < k || maxCount >= Objects.requireNonNull(minHeap.peek()).count) {
                    Node newNode = new Node(key, maxCount);
                    if (minHeap.size() >= k) {
                        // 堆已满，挤出最小的
                        expelled = minHeap.poll().key;
                        expelledQueue.offer(new Item(expelled, maxCount));
                    }
                    minHeap.add(newNode);
                    isHot = true;
                }
            }
            
            return new AddResult(expelled, isHot, key);
        }
    }
    
    @Override
    public List<Item> list() {
        synchronized (minHeap) {
            List<Item> result = new ArrayList<>(minHeap.size());
            for (Node node : minHeap) {
                result.add(new Item(node.key, node.count));
            }
            // 按计数降序排序
            result.sort((a, b) -> Integer.compare(b.count(), a.count()));
            return result;
        }
    }
    
    @Override
    public BlockingQueue<Item> expelled() {
        return expelledQueue;
    }
    
    @Override
    public void fading() {
        // 衰减所有桶的计数
        for (Bucket[] row : buckets) {
            for (Bucket bucket : row) {
                synchronized (bucket) {
                    bucket.count = bucket.count >> 1;
                }
            }
        }
        
        // 衰减堆中的计数
        synchronized (minHeap) {
            PriorityQueue<Node> newHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count));
            for (Node node : minHeap) {
                newHeap.add(new Node(node.key, node.count >> 1));
            }
            minHeap.clear();
            minHeap.addAll(newHeap);
        }
        
        total = total >> 1;
    }
    
    @Override
    public long total() {
        return total;
    }
    
    /**
     * 哈希函数（用于计算指纹）
     * 
     * @param data 数据
     * @return 哈希值
     */
    private static int hash(byte[] data) {
        return HashUtil.murmur32(data);
    }
    
    /**
     * 带种子的哈希函数（用于在不同层计算不同的桶位置）
     * <p>
     * 种子的作用：确保同一个 Key 在不同层映射到不同的桶位置
     * 实现原理：将种子与原始哈希值进行异或运算，产生不同的哈希值
     * 
     * @param data 数据
     * @param seed 种子（通常使用层数 i 作为种子）
     * @return 哈希值
     */
    private static int hash(byte[] data, int seed) {
        // 方案：将种子与哈希值进行混合
        // 使用异或和乘法来确保不同种子产生不同的哈希值
        int baseHash = HashUtil.murmur32(data);
        // 使用一个大质数与种子相乘，然后与基础哈希值异或
        return baseHash ^ (seed * 0x9e3779b9);
    }
    
    /**
     * 桶结构
     */
    private static class Bucket {
        /**
         * 指纹（用于识别 Key）
         */
        long fingerprint;
        
        /**
         * 计数
         */
        int count;
    }
    
    /**
     * 堆节点
     */
    private static class Node {
        final String key;
        final int count;
        
        Node(String key, int count) {
            this.key = key;
            this.count = count;
        }
    }
}
