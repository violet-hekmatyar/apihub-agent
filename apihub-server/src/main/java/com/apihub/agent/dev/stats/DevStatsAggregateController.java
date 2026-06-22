package com.apihub.agent.dev.stats;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ResultUtils;
import com.apihub.agent.model.dto.StatsAggregateRequest;
import com.apihub.agent.model.vo.StatsAggregateResponseVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/stats")
public class DevStatsAggregateController {

    private final StatsAggregateService statsAggregateService;

    public DevStatsAggregateController(StatsAggregateService statsAggregateService) {
        this.statsAggregateService = statsAggregateService;
    }

    @PostMapping("/aggregate")
    public BaseResponse<StatsAggregateResponseVO> aggregate(@RequestBody StatsAggregateRequest request) {
        return ResultUtils.success(statsAggregateService.aggregate(request));
    }
}
