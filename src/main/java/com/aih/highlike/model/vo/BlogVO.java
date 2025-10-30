package com.aih.highlike.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 博客视图对象
 */
@Data
public class BlogVO implements Serializable {

    /**
     * 博客ID
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 封面图片URL
     */
    private String coverImg;

    /**
     * 博客内容
     */
    private String content;

    /**
     * 点赞数
     */
    private Integer thumbCount;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 当前用户是否已点赞
     */
    private Boolean hasThumb;

    private static final long serialVersionUID = 1L;
}
