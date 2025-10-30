package com.aih.highlike.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 点赞记录实体
 */
@Data
@TableName("thumb")
public class Thumb implements Serializable {

    /**
     * 点赞记录ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 点赞用户ID
     */
    private Long userId;

    /**
     * 被点赞的博客ID
     */
    private Long blogId;

    /**
     * 点赞时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
