import React, { useRef } from 'react';
import { Button, Space, Divider, Tooltip } from 'antd';
import {
    SaveOutlined,
    UploadOutlined,
    DownloadOutlined,
    EyeOutlined,
    EditOutlined,
    AppstoreOutlined,
    LoadingOutlined,
} from '@ant-design/icons';

export interface HeaderProps {
    title?: string;
    onSave?: () => void;
    /** 导入：接收 File，由外层处理 API 调用 */
    onImport?: (file: File) => void;
    /** 导出：由外层处理快照获取和 API 调用 */
    onExport?: () => void;
    /** 当前模式 */
    mode?: 'design' | 'preview';
    onModeToggle?: () => void;
    /** 循环块数量（用于显示徽标） */
    loopBlockCount?: number;
    onManageLoopBlocks?: () => void;
    importing?: boolean;
    exporting?: boolean;
}

const Header: React.FC<HeaderProps> = ({
    title = '未命名报表',
    onSave,
    onImport,
    onExport,
    mode = 'design',
    onModeToggle,
    loopBlockCount = 0,
    onManageLoopBlocks,
    importing = false,
    exporting = false,
}) => {
    const fileInputRef = useRef<HTMLInputElement>(null);

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) onImport?.(file);
        e.target.value = '';
    };

    return (
        <div className="report-engine__header">
            <div className="report-engine__header-left">
                <span className="report-engine__header-title">{title}</span>
            </div>
            <div className="report-engine__header-right">
                <Space>
                    {onImport && (
                        <>
                            <input
                                ref={fileInputRef}
                                type="file"
                                accept=".xlsx"
                                style={{ display: 'none' }}
                                onChange={handleFileChange}
                            />
                            <Tooltip title="导入 Excel 文件">
                                <Button
                                    icon={importing ? <LoadingOutlined /> : <UploadOutlined />}
                                    disabled={importing}
                                    onClick={() => fileInputRef.current?.click()}
                                >
                                    导入
                                </Button>
                            </Tooltip>
                        </>
                    )}
                    {onExport && (
                        <Tooltip title="导出为 Excel 文件">
                            <Button
                                icon={exporting ? <LoadingOutlined /> : <DownloadOutlined />}
                                disabled={exporting}
                                onClick={onExport}
                            >
                                导出
                            </Button>
                        </Tooltip>
                    )}
                    {(onImport || onExport) && <Divider type="vertical" style={{ margin: 0 }} />}
                    <Tooltip title={mode === 'design' ? '切换到预览模式' : '切换到设计模式'}>
                        <Button
                            icon={mode === 'design' ? <EyeOutlined /> : <EditOutlined />}
                            onClick={onModeToggle}
                        >
                            {mode === 'design' ? '预览' : '设计'}
                        </Button>
                    </Tooltip>
                    <Divider type="vertical" style={{ margin: 0 }} />
                    <Tooltip title="管理循环块">
                        <Button
                            icon={<AppstoreOutlined />}
                            onClick={onManageLoopBlocks}
                        >
                            循环块{loopBlockCount > 0 ? ` (${loopBlockCount})` : ''}
                        </Button>
                    </Tooltip>
                    {onSave && (
                        <>
                            <Divider type="vertical" style={{ margin: 0 }} />
                            <Button type="primary" icon={<SaveOutlined />} onClick={onSave}>
                                保存
                            </Button>
                        </>
                    )}
                </Space>
            </div>
        </div>
    );
};

export default Header;
