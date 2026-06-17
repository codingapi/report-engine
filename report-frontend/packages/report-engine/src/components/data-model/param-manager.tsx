import { useState, useCallback } from 'react';
import { Button, List, Tag, Empty, Popconfirm, Typography } from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  HolderOutlined,
} from '@ant-design/icons';
import type { ReportParam } from '../../types';
import { genId } from '../../types';
import ParamModal from './param-modal';

const { Text } = Typography;

interface ParamManagerProps {
  params: ReportParam[];
  onChange: (params: ReportParam[]) => void;
}

/** 报表参数管理：列表展示 + 弹窗编辑 + 拖拽到报表画布。 */
const ParamManager: React.FC<ParamManagerProps> = ({ params, onChange }) => {
  const [modalOpen, setModalOpen] = useState(false);
  const [editingParam, setEditingParam] = useState<ReportParam | null>(null);

  const existingNames = params.map((p) => p.name);

  // ─── 操作 ──────────────────────────────────

  const openAdd = () => {
    setEditingParam(null);
    setModalOpen(true);
  };

  const openEdit = (param: ReportParam) => {
    setEditingParam(param);
    setModalOpen(true);
  };

  const handleConfirm = (param: ReportParam) => {
    if (editingParam) {
      onChange(params.map((p) => (p.id === editingParam.id ? { ...p, ...param } : p)));
    } else {
      onChange([...params, { ...param, id: genId() }]);
    }
    setModalOpen(false);
    setEditingParam(null);
  };

  const remove = useCallback(
    (id: string) => {
      onChange(params.filter((p) => p.id !== id));
    },
    [params, onChange],
  );

  // ─── 拖拽 ──────────────────────────────────

  const handleDragStart = (e: React.DragEvent, param: ReportParam) => {
    const dragData = {
      type: 'report-param',
      paramName: param.name,
      paramAlias: param.alias || param.name,
    };
    e.dataTransfer.setData('text/plain', JSON.stringify(dragData));
    e.dataTransfer.effectAllowed = 'copy';
  };

  // ─── 渲染 ──────────────────────────────────

  return (
    <div className="re-param-manager">
      <div className="re-param-intro">
        报表参数在设计时定义，渲染时由外部传值（缺省用默认值）。
        在表达式中通过 <code>{'${参数名}'}</code> 引用，或拖入报表单元格。
      </div>

      {params.length > 0 ? (
        <List
          className="re-param-list"
          size="small"
          dataSource={params}
          renderItem={(p) => (
            <List.Item
              className="re-param-list-item"
              draggable
              onDragStart={(e) => handleDragStart(e, p)}
              actions={[
                <Button
                  key="edit"
                  type="text"
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => openEdit(p)}
                />,
                <Popconfirm
                  key="del"
                  title="删除此参数？"
                  onConfirm={() => remove(p.id)}
                  okText="删除"
                  cancelText="取消"
                >
                  <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta
                avatar={
                  <HolderOutlined className="re-param-drag-handle" />
                }
                title={
                  <span>
                    <span className="re-param-alias">{p.alias || p.name}</span>
                    {p.alias && (
                      <Text className="re-param-name" type="secondary">
                        {p.name}
                      </Text>
                    )}
                  </span>
                }
                description={
                  <span>
                    <Tag style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>
                      {p.dataType}
                    </Tag>
                    {p.defaultValue ? (
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        默认: {p.defaultValue}
                      </Text>
                    ) : (
                      <Text type="warning" style={{ fontSize: 11 }}>
                        必填
                      </Text>
                    )}
                  </span>
                }
              />
            </List.Item>
          )}
        />
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="暂无参数"
          style={{ margin: '16px 0' }}
        />
      )}

      <Button
        type="dashed"
        size="small"
        icon={<PlusOutlined />}
        onClick={openAdd}
        block
        style={{ marginTop: 8 }}
      >
        添加参数
      </Button>

      <ParamModal
        open={modalOpen}
        editingParam={editingParam}
        existingNames={existingNames}
        onClose={() => {
          setModalOpen(false);
          setEditingParam(null);
        }}
        onConfirm={handleConfirm}
      />
    </div>
  );
};

export default ParamManager;
