import type { ConnectionFormProps } from '../types';
/**
 * 数据源连接配置表单（受控 + antd Form）。
 * 字段：name / type / url / username / password / options(JSON 文本)
 * 「测试连接」依赖注入的 service；未注入时按钮禁用。
 */
export default function ConnectionForm({ value, onChange, service, testResult, onTestResultChange, disabled, }: ConnectionFormProps): import("react").JSX.Element;
