import React from 'react';
import { Tooltip } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';

/** 带帮助提示的表单字段标签（循环块 / 扩展设置 / 条件弹窗 共用） */
const SectionLabel: React.FC<{ text: string; hint: string }> = ({ text, hint }) => (
  <div className="re-form-label">
    <span>{text}</span>
    <Tooltip title={hint}>
      <QuestionCircleOutlined className="re-form-label__hint" />
    </Tooltip>
  </div>
);

export default SectionLabel;
