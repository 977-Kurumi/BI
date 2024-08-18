package com.booooo.BIbackend.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.booooo.BIbackend.annotation.AuthCheck;
import com.booooo.BIbackend.bizmq.MyMessageProducer;
import com.booooo.BIbackend.common.BaseResponse;
import com.booooo.BIbackend.common.DeleteRequest;
import com.booooo.BIbackend.common.ErrorCode;
import com.booooo.BIbackend.common.ResultUtils;
import com.booooo.BIbackend.constant.CommonConstant;
import com.booooo.BIbackend.constant.FileConstant;
import com.booooo.BIbackend.constant.UserConstant;
import com.booooo.BIbackend.exception.BusinessException;
import com.booooo.BIbackend.exception.ThrowUtils;
import com.booooo.BIbackend.manager.AiManager;
import com.booooo.BIbackend.manager.RedisLimiterManager;
import com.booooo.BIbackend.model.dto.Chart.*;
import com.booooo.BIbackend.model.dto.file.UploadFileRequest;
import com.booooo.BIbackend.model.entity.Chart;
import com.booooo.BIbackend.model.entity.User;
import com.booooo.BIbackend.model.enums.FileUploadBizEnum;
import com.booooo.BIbackend.model.vo.BiResponse;
import com.booooo.BIbackend.service.ChartService;
import com.booooo.BIbackend.service.UserService;
import com.booooo.BIbackend.utils.ExcelUtils;
import com.booooo.BIbackend.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 帖子接口
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private MyMessageProducer myMessageProducer;
    // region 增删改查

    /**
     * 创建
     *
     * @param ChartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest ChartAddRequest, HttpServletRequest request) {
        if (ChartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart Chart = new Chart();
        BeanUtils.copyProperties(ChartAddRequest, Chart);
        User loginUser = userService.getLoginUser(request);
        Chart.setUserId(loginUser.getId());
        boolean result = chartService.save(Chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = Chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param ChartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest ChartUpdateRequest) {
        if (ChartUpdateRequest == null || ChartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart Chart = new Chart();
        BeanUtils.copyProperties(ChartUpdateRequest, Chart);
        List<String> tags = ChartUpdateRequest.getTags();
        long id = ChartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(Chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart Chart = chartService.getById(id);
        if (Chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(Chart);
    }

//    /**
//     * 分页获取列表（仅管理员）
//     *
//     * @param ChartQueryRequest
//     * @return
//     */
//    @PostMapping("/list/page")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
//    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest ChartQueryRequest) {
//        long current = ChartQueryRequest.getCurrent();
//        long size = ChartQueryRequest.getPageSize();
//        Page<Chart> ChartPage = ChartService.page(new Page<>(current, size),
//                getQueryWrapper(ChartQueryRequest));
//        return ResultUtils.success(ChartPage);
//    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                     HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }


    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param ChartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest ChartQueryRequest,
                                                       HttpServletRequest request) {
        if (ChartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        ChartQueryRequest.setUserId(loginUser.getId());
        long current = ChartQueryRequest.getCurrent();
        long size = ChartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> ChartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(ChartQueryRequest));
        return ResultUtils.success(ChartPage);
    }


    /**
     * 编辑（用户）
     *
     * @param ChartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest ChartEditRequest, HttpServletRequest request) {
        if (ChartEditRequest == null || ChartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart Chart = new Chart();
        BeanUtils.copyProperties(ChartEditRequest, Chart);
        User loginUser = userService.getLoginUser(request);
        long id = ChartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(Chart);
        return ResultUtils.success(result);
    }

    /**
     * 智能分析
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                             GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        String chartname = genChartByAiRequest.getChartname();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        //校验
        ThrowUtils.throwIf(!StringUtils.isNotBlank(goal), ErrorCode.PARAMS_ERROR, "分析目标为空");
        ThrowUtils.throwIf(!StringUtils.isNotBlank(chartname) && chartname.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        User loginUser = userService.getLoginUser(request);

        redisLimiterManager.doRateLimiter("genChartByAi_" + loginUser.getId());

        //分析需求：
        //分析网站用户的增长情况
        //原始数据：
        //日期，用户数
        //1号，10
        //2号，20
        //3号，50
        long size = multipartFile.getSize();
        // 取到原始文件名
        String originalFilename = multipartFile.getOriginalFilename();

        /*
          校验文件大小

          定义一个常量表示1MB;
          一兆(1MB) = 1024*1024字节(Byte) = 2的20次方字节
         */
        final long ONE_MB = 1024 * 1024L;
        // 如果文件大小,大于一兆,就抛出异常,并提示文件超过1M
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");

        /*
          校验文件后缀(一般文件是aaa.png,我们要取到.<点>后面的内容)

          利用FileUtil工具类中的getSuffix方法获取文件后缀名(例如:aaa.png,suffix应该保存为png)
         */
        String suffix = FileUtil.getSuffix(originalFilename);
        // 定义合法的后缀列表
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        // 如果suffix的后缀不在List的范围内,抛出异常,并提示'文件后缀非法'
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求").append("\n");
        String userGoal = goal;
        if (StringUtils.isNotBlank(userGoal)) {
            userGoal += "请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        //压缩后的数据
        String result = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(result).append("\n");
        long moduleId = 1821512376139636737L;
        String aiResult = aiManager.doChat(moduleId, userInput.toString());
        String[] splits = aiResult.split("【【【【【");
        if (splits.length < 3){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI 生成错误");
        }
        String genChat = splits[1].trim();
        String genResult = splits[2].trim();


        Chart chart = new Chart();
        chart.setChartname(chartname);
        chart.setGoal(goal);
        chart.setChartData(result);
        chart.setChartType(chartType);
        chart.setGenChart(genChat);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save,ErrorCode.SYSTEM_ERROR,"图表保存失败");
        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChat);
        biResponse.setGenResult(genResult);
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);

    }
    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        String chartname = genChartByAiRequest.getChartname();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        //校验
        ThrowUtils.throwIf(!StringUtils.isNotBlank(goal), ErrorCode.PARAMS_ERROR, "分析目标为空");
        ThrowUtils.throwIf(!StringUtils.isNotBlank(chartname) && chartname.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        User loginUser = userService.getLoginUser(request);

        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimiter("genChartByAi_" + loginUser.getId());


        //分析需求：
        //分析网站用户的增长情况
        //原始数据：
        //日期，用户数
        //1号，10
        //2号，20
        //3号，50
        long size = multipartFile.getSize();
        // 取到原始文件名
        String originalFilename = multipartFile.getOriginalFilename();

        /*
          校验文件大小

          定义一个常量表示1MB;
          一兆(1MB) = 1024*1024字节(Byte) = 2的20次方字节
         */
        final long ONE_MB = 1024 * 1024L;
        // 如果文件大小,大于一兆,就抛出异常,并提示文件超过1M
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");

        /*
          校验文件后缀(一般文件是aaa.png,我们要取到.<点>后面的内容)

          利用FileUtil工具类中的getSuffix方法获取文件后缀名(例如:aaa.png,suffix应该保存为png)
         */
        String suffix = FileUtil.getSuffix(originalFilename);
        // 定义合法的后缀列表
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        // 如果suffix的后缀不在List的范围内,抛出异常,并提示'文件后缀非法'
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求").append("\n");
        String userGoal = goal;
        if (StringUtils.isNotBlank(userGoal)) {
            userGoal += "请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        //压缩后的数据
        String result = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(result).append("\n");
        long moduleId = 1821512376139636737L;
        // 先把图表保存到数据库中

        Chart chart = new Chart();
        chart.setChartname(chartname);
        chart.setGoal(goal);
        chart.setChartData(result);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save,ErrorCode.SYSTEM_ERROR,"图表保存失败");

        // 在最终的返回结果前提交一个任务
        CompletableFuture.runAsync(() ->{
            // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。(为了防止同一个任务被多次执行)
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());// 把任务状态改为执行中
            updateChart.setStatus("running");
            boolean b = chartService.updateById(updateChart);
            // 如果提交失败(一般情况下,更新失败可能意味着你的数据库出问题了)
            if (!b) {
                handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
            }
            //调用AI
            String aiResult = aiManager.doChat(moduleId, userInput.toString());
            String[] splits = aiResult.split("【【【【【");
            if (splits.length < 3){
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI 生成错误");
            }
            // 调用AI得到结果之后,再更新一次
            String genChat = splits[1].trim();
            String genResult = splits[2].trim();
            Chart updateChartResult = new Chart();
            updateChartResult.setId(chart.getId());
            updateChartResult.setGenChart(genChat);
            updateChartResult.setGenResult(genResult);
            updateChartResult.setStatus("succeed");
            boolean updateResult = chartService.updateById(updateChartResult);
            if (!updateResult) {
                handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
            }
        },threadPoolExecutor);

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);

    }
    // 上面的接口很多用到异常,直接定义一个工具类
    private void handleChartUpdateError(Long chartId, String msg) {
        Chart chart = new Chart();
        chart.setId(chartId);
        chart.setStatus("failed");
        chart.setExecMessage(msg);
        boolean b = chartService.updateById(chart);
        if (!b) {
            log.error("更新图表失败"+chartId+","+msg);
        }
    }

    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        String chartname = genChartByAiRequest.getChartname();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        //校验
        ThrowUtils.throwIf(!StringUtils.isNotBlank(goal), ErrorCode.PARAMS_ERROR, "分析目标为空");
        ThrowUtils.throwIf(!StringUtils.isNotBlank(chartname) && chartname.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        User loginUser = userService.getLoginUser(request);

        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimiter("genChartByAi_" + loginUser.getId());


        //分析需求：
        //分析网站用户的增长情况
        //原始数据：
        //日期，用户数
        //1号，10
        //2号，20
        //3号，50
        long size = multipartFile.getSize();
        // 取到原始文件名
        String originalFilename = multipartFile.getOriginalFilename();

        /*
          校验文件大小

          定义一个常量表示1MB;
          一兆(1MB) = 1024*1024字节(Byte) = 2的20次方字节
         */
        final long ONE_MB = 1024 * 1024L;
        // 如果文件大小,大于一兆,就抛出异常,并提示文件超过1M
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");

        /*
          校验文件后缀(一般文件是aaa.png,我们要取到.<点>后面的内容)

          利用FileUtil工具类中的getSuffix方法获取文件后缀名(例如:aaa.png,suffix应该保存为png)
         */
        String suffix = FileUtil.getSuffix(originalFilename);
        // 定义合法的后缀列表
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        // 如果suffix的后缀不在List的范围内,抛出异常,并提示'文件后缀非法'
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        //压缩后的数据
        String result = ExcelUtils.excelToCsv(multipartFile);

        // 先把图表保存到数据库中
        Chart chart = new Chart();
        chart.setChartname(chartname);
        chart.setGoal(goal);
        chart.setChartData(result);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save,ErrorCode.SYSTEM_ERROR,"图表保存失败");

        // 在最终的返回结果前提交一个任务
        CompletableFuture.runAsync(() ->{
        },threadPoolExecutor);

        myMessageProducer.sendMessage(String.valueOf(chart.getId()));
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);

    }

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String chartname = chartQueryRequest.getChartname();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(chartname), "chartname", chartname);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }


}
