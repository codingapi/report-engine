import React from 'react';
import { Modal, Table } from 'antd';
import type { DrillResult } from '@coding-report/report-api';

interface DrillModalProps {
  open: boolean;
  loading: boolean;
  result?: DrillResult;
  onClose: () => void;
}

/**
 * 反查明细弹窗：展示选中单元格贡献的原始数据行。
 * 本期一次性返回全部明细，不分页；后续三期再加统一分页接口。
 */
const DrillModal: React.FC<DrillModalProps> = ({ open, loading, result, onClose }) => {
  if (!result) {
    return (
      <Modal
        title="反查明细"
        open={open}
        onCancel={onClose}
        footer={null}
        width="80%"
        zIndex={1050}
        styles={{ body: { maxHeight: '70vh', overflow: 'auto' } }}
      >
        加载中...
      </Modal>
    );
  }

  const { datasetId, alias, fields, rows } = result;

  const columns = fields.map((f: { name: string; alias: string | null }) => ({
    title: f.alias || f.name,
    dataIndex: f.name,
    key: f.name,
  }));

  return (
    <Modal
      title={`反查明细：${alias || datasetId || '未知数据集'}`}
      open={open}
      onCancel={onClose}
      footer={null}
      width="80%"
      zIndex={1050}
      styles={{ body: { maxHeight: '70vh', overflow: 'auto' } }}
    >
      {rows.length === 0 ? (
        <div style={{ padding: 24, textAlign: 'center', color: '#999' }}>无明细数据</div>
      ) : (
        <Table
          dataSource={rows}
          columns={columns}
          rowKey={(record, index) => String(index)}
          size="small"
          pagination={{ pageSize: 20 }}
          loading={loading}
          scroll={{ x: 'max-content' }}
        />
      )}
    </Modal>
  );
};

export default DrillModal;
