import React from 'react';
import { Tooltip } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';

/** 带帮助提示的小节标签（循环块 / 小计配置共用） */
const SectionLabel: React.FC<{ text: string; hint: string }> = ({ text, hint }) => (
  <div className="re-loop-label">
    <span>{text}</span>
    <Tooltip title={hint}>
      <QuestionCircleOutlined className="re-loop-label__hint" />
    </Tooltip>
  </div>
);

export default SectionLabel;
