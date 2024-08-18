package com.booooo.BIbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.booooo.BIbackend.mapper.ChartMapper;
import com.booooo.BIbackend.model.entity.Chart;
import com.booooo.BIbackend.service.ChartService;
import org.springframework.stereotype.Service;

/**
* @author 24822
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
* @createDate 2024-08-07 23:01:35
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService{

}




