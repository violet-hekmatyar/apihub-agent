package com.apihub.agent.service;

import com.apihub.agent.common.ErrorCode;
import com.apihub.agent.common.PageResponse;
import com.apihub.agent.exception.BusinessException;
import com.apihub.agent.model.vo.UserVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<UserVO> userRowMapper = (rs, rowNum) -> {
        UserVO vo = new UserVO();
        vo.setId(rs.getLong("id"));
        vo.setUsername(rs.getString("username"));
        vo.setDisplayName(rs.getString("display_name"));
        vo.setUserType(rs.getString("user_type"));
        vo.setOrgName(rs.getString("org_name"));
        vo.setStatus(rs.getString("status"));
        return vo;
    };

    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserVO getCurrentUser(Long userId) {
        Long actualUserId = userId == null ? 1L : userId;
        return getActiveUserById(actualUserId);
    }

    public PageResponse<UserVO> listActiveUsers(Integer pageNo, Integer pageSize) {
        int actualPageNo = normalizePageNo(pageNo);
        int actualPageSize = normalizePageSize(pageSize);
        int offset = (actualPageNo - 1) * actualPageSize;
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE status = 'ACTIVE'",
                Long.class
        );
        List<UserVO> items = jdbcTemplate.query(
                """
                SELECT id, username, display_name, user_type, org_name, status
                FROM sys_user
                WHERE status = 'ACTIVE'
                ORDER BY id ASC
                LIMIT ? OFFSET ?
                """,
                userRowMapper,
                actualPageSize,
                offset
        );
        return new PageResponse<>(items, total == null ? 0 : total, actualPageNo, actualPageSize);
    }

    public UserVO switchUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
        return getActiveUserById(userId);
    }

    private UserVO getActiveUserById(Long userId) {
        List<UserVO> users = jdbcTemplate.query(
                """
                SELECT id, username, display_name, user_type, org_name, status
                FROM sys_user
                WHERE id = ? AND status = 'ACTIVE'
                """,
                userRowMapper,
                userId
        );
        if (users.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        }
        return users.get(0);
    }

    private int normalizePageNo(Integer pageNo) {
        if (pageNo == null) {
            return DEFAULT_PAGE_NO;
        }
        return Math.max(pageNo, DEFAULT_PAGE_NO);
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
