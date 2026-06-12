/**
 * 默认循环块面板组件
 * 提供基础的循环块配置 UI，使用方可直接使用或自行替换
 */

import React from 'react';
import type { LoopBlockComponentProps } from '@/types';

/**
 * 默认循环块面板
 * 显示循环块基本信息和循环变量配置
 */
export const DefaultLoopBlockPanel: React.FC<LoopBlockComponentProps> = ({
    block,
    onDelete,
}) => {
    return (
        <div style={{
            padding: '8px 12px',
            background: '#f6ffed',
            border: '1px solid #b7eb8f',
            borderRadius: 6,
            fontSize: 12,
        }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                <span style={{ fontWeight: 500 }}>
                    {block.label || `循环块 ${block.id}`}
                </span>
                <button
                    onClick={onDelete}
                    style={{
                        background: 'none',
                        border: 'none',
                        color: '#ff4d4f',
                        cursor: 'pointer',
                        fontSize: 12,
                        padding: '0 4px',
                    }}
                >
                    删除
                </button>
            </div>
            <div style={{ color: '#666' }}>
                范围: ({block.startRow},{block.startColumn}) → ({block.endRow},{block.endColumn})
            </div>
        </div>
    );
};
