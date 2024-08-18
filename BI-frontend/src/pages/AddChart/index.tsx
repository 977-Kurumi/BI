// import Footer from '@/components/Footer';
// import { getFakeCaptcha } from '@/services/ant-design-pro/login';
import React, {useState} from 'react';
// import Settings from '../../../../config/defaultSettings';

import {Button, Card, Col, Divider, Form, Input, message, Row, Select, Space, Spin, Upload} from 'antd';
import TextArea from "antd/es/input/TextArea";
import {UploadOutlined} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import {Helmet} from "@@/exports";
import Settings from "../../../config/defaultSettings";
import {genChartByAiUsingPost} from "@/services/BI/chartController";

/**
 * 添加图表页面
 * @constructor
 */
const AddChart: React.FC = () => {
  const [chart,setChart] = useState<API.BiResponse>();
  const [submitting,setSubmitting] = useState<boolean>(false);
  const [option,setOption] = useState<any>();

  /**
   * 表单提交方法
   * @param values
   */
  const onFinish = async (values: any) => {
    if (submitting){
      return
    }
    setOption(undefined);
    setChart(undefined);
    setSubmitting(true);
    const params = {
      ...values,
      file: undefined
    }
    try {
      //对接后端
      const res = await genChartByAiUsingPost(params, {}, values.file.file.originFileObj)
      console.log(res)
      if (!res?.data){
        message.error('分析失败')
      }
      else {
        message.success('分析成功')
        const chartOption = JSON.parse(res.data.genChart ?? "")
        if (!chartOption){
          throw new Error("图表代码解析错误")
        }
        setChart(res.data);
        setOption(chartOption);
      }
    } catch (e: any) {
      message.error('分析失败'+e.message)
    }
    setSubmitting(false);
  };
  return (
    <div className="add-chart">
      <Helmet>
        <title>
          {'智能分析'}- {Settings.title}
        </title>
      </Helmet>
      <Row gutter={24}>
        <Col span={12}>
          <Card title={'智能分析'}>
            <Form
              name="addChart"
              labelAlign={'left'}
              labelCol={{ span: 4 }}
              wrapperCol={{ span: 16 }}
              onFinish={onFinish}
              initialValues={{}}
            >
              <Form.Item
                name="goal"
                label="分析目标："
                rules={[{ required: true, message: '请输入分析目标' }]}
              >
                <TextArea placeholder="请输入您的分析诉求" />
              </Form.Item>
              <Form.Item name="chartname" label="图表名称：">
                <Input placeholder="请输入图表名称" />
              </Form.Item>
              <Form.Item name="chartType" label="图表类型">
                <Select
                  options={[
                    { value: '折线图', label: '折线图' },
                    { value: '柱状图', label: '柱状图' },
                    { value: '堆叠图', label: '堆叠图' },
                    { value: '饼图', label: '饼图' },
                    { value: '雷达图', label: '雷达图' },
                  ]}
                ></Select>
              </Form.Item>

              <Form.Item name="file" label="原始数据">
                <Upload name="file" maxCount={1}>
                  <Button icon={<UploadOutlined />}>上传 CSV 文件</Button>
                </Upload>
              </Form.Item>

              <Form.Item wrapperCol={{ span: 16, offset: 4 }}>
                <Space>
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={submitting}
                    disabled={submitting}
                  >
                    智能分析
                  </Button>
                  <Button htmlType="reset">重置</Button>
                </Space>
              </Form.Item>
            </Form>
          </Card>
        </Col>
        <Col span={12}>
          <Card title={'分析结论'}>
            {submitting ? (
              <Spin spinning={submitting} />
            ) : (
              chart?.genResult ? (
                chart.genResult // 假设chart.genResult是可以直接渲染的React元素或组件
              ) : (
                <div>请先在左侧提交数据</div>
              )
            )}
          </Card>
          <Divider />
          <Card title={'数据可视化'}>
            {/* 当数据正在提交时，只显示加载动画 */}
            {submitting ? (
              <Spin spinning={submitting} />
            ) : (
              /* 当数据提交完成后，根据option是否存在来决定显示什么 */
              option ? (
                <ReactECharts option={option} />
              ) : (
                <div>请先在左侧提交数据</div>
              )
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
};
export default AddChart;
