import React from 'react';
import { Button, Input, Select, Empty } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ReportParam, DataType } from '../../types';
import { genId } from '../../types';

interface ParamManagerProps {
  params: ReportParam[];
  onChange: (params: ReportParam[]) => void;
}

const DATA_TYPES: DataType[] = ['STRING', 'NUMBER', 'DATE', 'DATETIME', 'BOOLEAN'];

/** 报表参数管理：增删改（名称/类型/默认值）。可在表达式构建器中以 ${name} 引用。 */
const ParamManager: React.FC<ParamManagerProps> = ({ params, onChange }) => {
  const add = () => {
    onChange([...params, { id: genId(), name: '', dataType: 'STRING', defaultValue: '' }]);
  };
  const update = (id: string, patch: Partial<ReportParam>) => {
    onChange(params.map((p) => (p.id === id ? { ...p, ...patch } : p)));
  };
  const remove = (id: string) => {
    onChange(params.filter((p) => p.id !== id));
  };

  return (
    <div className="re-param-manager">
      <div className="re-param-intro">
        报表参数在设计时定义，渲染时可由外部传值（缺省用默认值）。
        在表达式中通过 <code>{'${参数名}'}</code> 引用。
      </div>

      {params.length > 0 ? (
        <div className="re-param-list">
          {params.map((p) => (
            <div key={p.id} className="re-param-item">
              <Input
                size="small"
                value={p.name}
                onChange={(e) => update(p.id, { name: e.target.value })}
                placeholder="参数名（如 year）"
              />
              <Select
                size="small"
                value={p.dataType}
                onChange={(dt: DataType) => update(p.id, { dataType: dt })}
                style={{ width: 92, flexShrink: 0 }}
                options={DATA_TYPES.map((t) => ({ value: t, label: t }))}
              />
              <Input
                size="small"
                value={p.defaultValue}
                onChange={(e) => update(p.id, { defaultValue: e.target.value })}
                placeholder="默认值"
              />
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={() => remove(p.id)}
              />
            </div>
          ))}
        </div>
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="暂无参数"
          style={{ margin: '16px 0' }}
        />
      )}

      <Button type="dashed" size="small" icon={<PlusOutlined />} onClick={add} block style={{ marginTop: 8 }}>
        添加参数
      </Button>
    </div>
  );
};

export default ParamManager;
