package com.apihub.agent.controller;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.PageResponse;
import com.apihub.agent.common.ResultUtils;
import com.apihub.agent.model.dto.UserSwitchRequest;
import com.apihub.agent.model.vo.UserVO;
import com.apihub.agent.service.UserService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/current")
    public BaseResponse<UserVO> currentUser(
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long userId
    ) {
        return ResultUtils.success(userService.getCurrentUser(userId));
    }

    @GetMapping
    public BaseResponse<PageResponse<UserVO>> listUsers(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        return ResultUtils.success(userService.listActiveUsers(pageNo, pageSize));
    }

    @PostMapping("/switch")
    public BaseResponse<UserVO> switchUser(@Valid @RequestBody UserSwitchRequest request) {
        return ResultUtils.success(userService.switchUser(request.getUserId()));
    }
}
