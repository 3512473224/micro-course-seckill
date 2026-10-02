package com.mall.enroll.feign;

import com.mall.common.dto.CourseDTO;
import com.mall.common.dto.CoursePhaseDTO;
import com.mall.common.dto.IdListRequest;
import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * enroll-service -> course-service：
 *  - 建单时校验课程是否存在/是否上架；
 *  - 批量拉课程做时间冲突/学分校验；
 *  - 读当前选课阶段做阶段门控；志愿结算时核验阶段并标记阶段结束。
 */
@FeignClient(name = "course-service", path = "/api/course")
public interface CourseFeignClient {

    @GetMapping("/{id}")
    Result<CourseDTO> getById(@PathVariable("id") Long id);

    /** 批量查课程：时间冲突检测一次拉取该生所有已选课程，避免 N+1 */
    @PostMapping("/batch")
    Result<List<CourseDTO>> batch(@RequestBody IdListRequest request);

    /** 当前进行中的选课阶段（无则 data 为 null） */
    @GetMapping("/phase/current")
    Result<CoursePhaseDTO> currentPhase();

    /** 按 id 查阶段：志愿结算时核验 phaseId */
    @GetMapping("/phase/{id}")
    Result<CoursePhaseDTO> phaseById(@PathVariable("id") Long id);

    /** 志愿结算完成后把阶段置为已结束（服务间直调，不走网关） */
    @PostMapping("/admin/phase/{id}/finish")
    Result<Void> finishPhase(@PathVariable("id") Long id);
}
