package com.aih.highlike.model.dto.thumb;

import lombok.Data;

import java.io.Serializable;

/**
 * 点赞请求
 */
@Data
public class ThumbRequest implements Serializable {

    /**
     * 博客ID
     */
    private Long blogId;

    private static final long serialVersionUID = 1L;
}
