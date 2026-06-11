import React from 'react';
import { Button, Space } from 'antd';
import { SaveOutlined } from '@ant-design/icons';

export interface HeaderProps {
    /** 报表名称 */
    title?: string;
    /** 保存按钮点击 */
    onSave?: () => void;
}

const Header: React.FC<HeaderProps> = ({
    title = '未命名报表',
    onSave,
}) => {
    return (
        <div className="report-engine__header">
            <div className="report-engine__header-left">
                <span className="report-engine__header-title">{title}</span>
            </div>
            <div className="report-engine__header-right">
                <Space>
                    {onSave && (
                        <Button icon={<SaveOutlined />} onClick={onSave}>
                            保存
                        </Button>
                    )}
                </Space>
            </div>
        </div>
    );
};

export default Header;
