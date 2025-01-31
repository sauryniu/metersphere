package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.sdk.BasePageRequest;
import io.metersphere.system.dto.taskcenter.request.TaskCenterBatchRequest;
import io.metersphere.system.dto.taskhub.*;
import io.metersphere.system.dto.taskhub.request.TaskHubItemRequest;
import io.metersphere.system.dto.taskhub.response.TaskStatisticsResponse;
import io.metersphere.system.log.annotation.Log;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.service.BaseTaskHubLogService;
import io.metersphere.system.service.BaseTaskHubService;
import io.metersphere.system.service.SystemOrganizationLogService;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统任务中心")
@RestController
@RequestMapping("/system/task-center")
public class SystemTaskHubController {

    @Resource
    private BaseTaskHubService baseTaskHubService;

    @PostMapping("/exec-task/page")
    @Operation(summary = "系统-任务中心-执行任务列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_CASE_TASK_CENTER_READ)
    public Pager<List<TaskHubDTO>> execTaskList(@Validated @RequestBody BasePageRequest request) {
        return baseTaskHubService.getTaskList(request, null, null);
    }


    @PostMapping("/schedule/page")
    @Operation(summary = "系统-任务中心-后台执行任务列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_SCHEDULE_TASK_CENTER_READ)
    public Pager<List<TaskHubScheduleDTO>> scheduleList(@Validated @RequestBody BasePageRequest request) {
        return baseTaskHubService.getScheduleTaskList(request, null);
    }

    @PostMapping("/exec-task/item/page")
    @Operation(summary = "系统-任务中心-用例执行任务详情列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_CASE_TASK_CENTER_READ)
    public Pager<List<TaskHubItemDTO>> itemPageList(@Validated @RequestBody TaskHubItemRequest request) {
        return baseTaskHubService.getCaseTaskItemList(request, null, null);
    }


    @PostMapping("/exec-task/statistics")
    @Operation(summary = "系统-任务中心-获取任务统计{通过率}接口")
    @RequiresPermissions(PermissionConstants.SYSTEM_CASE_TASK_CENTER_READ)
    @Parameter(name = "ids", description = "任务ID集合", schema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
    public List<TaskStatisticsResponse> calculateRate(@RequestBody List<String> ids) {
        return baseTaskHubService.calculateRate(ids, null, null);
    }


    @GetMapping("/resource-pool/options")
    @Operation(summary = "系统-任务中心-获取资源池下拉选项")
    @RequiresPermissions(PermissionConstants.SYSTEM_CASE_TASK_CENTER_READ)
    public List<ResourcePoolOptionsDTO> getUserProject() {
        return baseTaskHubService.getResourcePoolOptions();
    }

    @PostMapping("/resource-pool/status")
    @Operation(summary = "任务详情节点状态接口")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_CASE_TASK_CENTER_READ, PermissionConstants.ORGANIZATION_CASE_TASK_CENTER_READ, PermissionConstants.PROJECT_CASE_TASK_CENTER_READ}, logical = Logical.OR)
    @Parameter(name = "ids", description = "详情ID集合", schema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
    public List<ResourcePoolStatusDTO> resourcePoolStatus(@RequestBody List<String> ids) {
        return baseTaskHubService.getResourcePoolStatus(ids);
    }


    @GetMapping("/exec-task/stop/{id}")
    @Operation(summary = "系统-任务中心-用例执行任务-停止任务")
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.systemStopLog(#id)", msClass = BaseTaskHubLogService.class)
    @RequiresPermissions(PermissionConstants.SYSTEM_CASE_TASK_CENTER_EXEC_STOP)
    public void stopTask(@PathVariable String id) {
        baseTaskHubService.stopTask(id, SessionUtils.getUserId(), null, null);
    }


    @GetMapping("/exec-task/delete/{id}")
    @Operation(summary = "系统-任务中心-用例执行任务-删除任务")
    @Log(type = OperationLogType.DELETE, expression = "#msClass.systemDeleteLog(#id)", msClass = BaseTaskHubLogService.class)
    @RequiresPermissions(PermissionConstants.SYSTEM_CASE_TASK_CENTER_DELETE)
    public void deleteTask(@PathVariable String id) {
        baseTaskHubService.deleteTask(id, null, null);
    }

    //TODO 系统&组织&项目 任务按钮操作：删除 停止 失败重跑 查看报告   批量删除 批量停止  批量失败重跑


    //TODO 系统&组织&项目 任务详情按钮操作：查看 停止  批量停止


    //TODO 系统&组织&项目 后台任务操作：删除  批量开启  批量关闭
}
