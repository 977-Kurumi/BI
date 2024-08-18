import React, {useEffect, useState} from 'react';
import {Avatar, Card, List, message} from "antd";
import {useModel} from "@umijs/max";
import Search from "antd/es/input/Search";
import ReactECharts from "echarts-for-react";
import {listChartByPageUsingPost} from "@/services/BI/chartController";
import Result from 'antd/lib/result';
/**
 * 我的图表页面
 * @constructor
 */
const MyChartPage: React.FC = () => {


// 把初始条件分离出来，便于后面恢复初始条件
  const initSearchParams = {

    // 默认第一页
    current:1,
    // 初始情况下返回每页12条数据
    pageSize:4,
    sortField:'createtime',
    sortOrder:'desc'
  };
  // 从全局状态中获取到当前登录的用户信息
  const{ initialState } = useModel('@@initialState');
  const { currentUser } = initialState ?? {};
  // 定义变量存储图表数据
  const [chartList, setChartList] = useState<API.Chart[]>();
  // 数据总数，类型为number，默认为0
  const [total, setTotal] = useState<number>(0);
  /*
    定义了一个状态(searchParams)和它对应的更新函数(setSearchParams)，并初始化为initSearchParams;
    searchParams是我们要发送给后端的查询条件，它的参数类型是API.ChartQueryRequest;
     {...} 是展开语法，它将 initSearchParams 中的所有属性展开并复制到一个新对象中，而不改变原始对象,因此可以避免在现有对象上直接更改值的对象变异操作。
     因为在 React 中，不推荐直接修改状态或属性，而是创建一个新对象并将其分配给状态或属性，这个方法就非常有用。
  */
  const [searchParams, setSearchParams] = useState<API.ChartQueryRequest>({ ...initSearchParams });
  // 加载状态，用来控制页面是否加载，默认正在加载
  const [loading, setLoading] = useState<boolean>(true);
  // 定义一个获取数据的异步函数
  const loadData = async () => {
    /*
      调用后端的接口，并传入searchParams作为请求参数,返回一个响应res;
      listMyChartByPageUsingPOST方法是通过openapi根据Swagger接口文档自动生成的;
      当searchParams状态改变时，可以通过setSearchParams更新该状态并重新获取数据
    */
    setLoading(true);
    try {
      const res = await listChartByPageUsingPost(searchParams);
      if (res.data){
        setChartList(res.data.records ?? [])
        setTotal(res.data.total ?? 0);
        // 有些图表有标题,有些没有,直接把标题全部去掉
        if (res.data.records) {
          res.data.records.forEach(data => {
            if (data.status === 'succeed'){
              // 要把后端返回的图表字符串改为对象数组,如果后端返回空字符串，就返回'{}'
              const chartOption = JSON.parse(data.genChart ?? '{}');
              // 把标题设为undefined
              chartOption.title = undefined;
              // 然后把修改后的数据转换为json设置回去
              data.genChart = JSON.stringify(chartOption);
            }
          })
        }
      }else {
        message.error("获取我的图表数据失败")
      }
    } catch (e:any) {
      message.error("获取我的图表数据失败"+e.message)
    }
    // 获取数据后，加载完毕，设置为false
    setLoading(false);
  }
  useEffect(() => {
    // 这个页面首次渲染的时候，以及这个数组中的搜索条件发生变化的时候，会执行loadData方法,自动触发重新搜索
    loadData();
  },[searchParams]);

  return (
    <div className="my-chart-page">
      {/* 引入搜索框 */}
      <div>
        {/*
          当用户点击搜索按钮触发 一定要把新设置的搜索条件初始化，要把页面切回到第一页;
          如果用户在第二页,输入了一个新的搜索关键词,应该重新展示第一页,而不是还在搜第二页的内容
        */}
        <Search
          placeholder="请输入图表名称"
          enterButton
          loading={loading}
          onSearch={(value) => {
            // 设置搜索条件
            setSearchParams({
              // 原始搜索条件
              ...initSearchParams,
              // 搜索词
              chartname: value,
            });
          }}
        />
        <div style={{ marginBottom: 16 }} />
      </div>
      <div className={'.margin-16'}>
        <List
          /*
            栅格间隔16像素;xs屏幕<576px,栅格数1;
            sm屏幕≥576px，栅格数1;md屏幕≥768px,栅格数1;
            lg屏幕≥992px,栅格数2;xl屏幕≥1200px,栅格数2;
            xxl屏幕≥1600px,栅格数2
          */
          grid={{
            gutter: 16,
            xs: 1,
            sm: 1,
            md: 1,
            lg: 2,
            xl: 2,
            xxl: 2,
          }}
          pagination={{
            /*
          page第几页，pageSize每页显示多少条;
          当用户点击这个分页组件,切换分页时,这个组件就会去触发onChange方法,会改变咱们现在这个页面的搜索条件
        */
            onChange: (page, pageSize) => {
              // 当切换分页，在当前搜索条件的基础上，把页数调整为当前的页数
              setSearchParams({
                ...searchParams,
                current: page,
                pageSize,
              });
            },
            // 显示当前页数
            current: searchParams.current,
            // 页面参数改成自己的
            pageSize: searchParams.pageSize,
            // 总数设置成自己的
            total: total,
          }}
          // 设置成我们的加载状态
          loading={loading}
          dataSource={chartList}
          renderItem={(item) => (
            <List.Item key={item.id}>
              {/* 用卡片包裹 */}
              <Card style={{ width: '100%' }}>
                <List.Item.Meta
                  // 把当前登录用户信息的头像展示出来
                  avatar={<Avatar src={currentUser && currentUser?.data?.userAvatar} />}
                  title={item.chartname}
                  description={item.chartType ? '图表类型：' + item.chartType : undefined}
                />
                <>
                  {
                    // 当状态（item.status）为'wait'时，显示待生成的结果组件
                    item.status === 'wait' && (
                      <>
                        <Result
                          // 状态为警告
                          status="warning"
                          title="待生成"
                          // 子标题显示执行消息，如果执行消息为空，则显示'当前图表生成队列繁忙，请耐心等候'
                          subTitle={item.execMessage ?? '当前图表生成队列繁忙，请耐心等候'}
                        />
                      </>
                    )
                  }
                  {item.status === 'running' && (
                    <>
                      <Result
                        // 状态为信息
                        status="info"
                        title="图表生成中"
                        // 子标题显示执行消息
                        subTitle={item.execMessage}
                      />
                    </>
                  )}
                  {
                    // 当状态（item.status）为'succeed'时，显示生成的图表
                    item.status === 'succeed' && (
                      <>
                        <div style={{ marginBottom: 16 }} />
                        <p>{'分析目标：' + item.goal}</p>
                        <div style={{ marginBottom: 16 }} />
                        <ReactECharts option={item.genChart && JSON.parse(item.genChart)} />
                        <p>{'分析结果：' + item.genResult}</p>
                      </>
                    )
                  }
                  {
                    // 当状态（item.status）为'failed'时，显示生成失败的结果组件
                    item.status === 'failed' && (
                      <>
                        <Result status="error" title="图表生成失败" subTitle={item.execMessage} />
                      </>
                    )
                  }
                </>
              </Card>
            </List.Item>
          )}
        />
      </div>
    </div>
  );
};
export default MyChartPage
