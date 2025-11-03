package com.aih.highlike.manager.cache;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * TopK 热点检测接口
 */
public interface TopK {
    
    /**
     * 添加元素并更新 TopK
     *
     * @param key       键
     * @param increment 增量
     * @return 添加结果
     */
    AddResult add(String key, int increment);
    
    /**
     * 获取当前 TopK 列表
     *
     * @return TopK 列表
     */
    List<Item> list();
    
    /**
     * 获取被挤出的元素队列
     *
     * @return 被挤出的元素队列
     */
    BlockingQueue<Item> expelled();
    
    /**
     * 衰减所有计数
     */
    void fading();
    
    /**
     * 获取总访问次数
     *
     * @return 总访问次数
     */
    long total();
}
