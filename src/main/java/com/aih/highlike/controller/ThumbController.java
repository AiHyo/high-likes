package com.aih.highlike.controller;

import com.aih.highlike.common.BaseResponse;
import com.aih.highlike.common.ResultUtils;
import com.aih.highlike.exception.BusinessException;
import com.aih.highlike.exception.ErrorCode;
import com.aih.highlike.model.dto.thumb.ThumbRequest;
import com.aih.highlike.service.ThumbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞控制器
 */
@RestController
@RequestMapping("/thumb")
@Tag(name = "点赞接口")
public class ThumbController {

    @Resource
    private ThumbService thumbService;

    /**
     * 点赞
     */
    @PostMapping("/do")
    @Operation(summary = "点赞")
    public BaseResponse<Boolean> doThumb(@RequestBody ThumbRequest thumbRequest, HttpServletRequest request) {
        if (thumbRequest == null || thumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = thumbService.doThumb(thumbRequest.getBlogId(), request);
        return ResultUtils.success(result);
    }

    /**
     * 取消点赞
     */
    @PostMapping("/cancel")
    @Operation(summary = "取消点赞")
    public BaseResponse<Boolean> cancelThumb(@RequestBody ThumbRequest thumbRequest, HttpServletRequest request) {
        if (thumbRequest == null || thumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = thumbService.cancelThumb(thumbRequest.getBlogId(), request);
        return ResultUtils.success(result);
    }
}
