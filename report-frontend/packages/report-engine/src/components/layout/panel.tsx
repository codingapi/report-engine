import React, { useState } from 'react';
import { Button } from 'antd';
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import { Panel as ResizablePanel, Separator, usePanelRef } from 'react-resizable-panels';

export type PanelPosition = 'left' | 'center' | 'right';

interface PanelProps {
    title?: string;
    position?: PanelPosition;
    defaultSize?: number | string;
    minSize?: number | string;
    withSeparator?: boolean;
    collapsible?: boolean;
    collapsedSize?: number | string;
    children?: React.ReactNode;
}

const Panel: React.FC<PanelProps> = ({ title, position, defaultSize, minSize, withSeparator, collapsible, collapsedSize = '36px', children }) => {
    const panelRef = usePanelRef();
    const [collapsed, setCollapsed] = useState(false);

    const toggle = () => {
        if (panelRef.current) {
            if (panelRef.current.isCollapsed()) {
                panelRef.current.expand();
            } else {
                panelRef.current.collapse();
            }
        }
    };

    const className = [
        'report-engine__panel',
        position ? `report-engine__panel--${position}` : '',
        collapsible ? 'report-engine__panel--collapsible' : '',
        collapsed ? 'report-engine__panel--collapsed' : '',
    ].filter(Boolean).join(' ');

    const getIcon = () => {
        if (collapsed) {
            return position === 'right' ? <MenuFoldOutlined /> : <MenuUnfoldOutlined />;
        }
        return position === 'right' ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />;
    };

    return (
        <>
            {withSeparator && <Separator className="report-engine__resize-handle" />}
            <ResizablePanel
                defaultSize={defaultSize}
                minSize={minSize}
                panelRef={panelRef}
                collapsible={collapsible}
                collapsedSize={collapsible ? collapsedSize : undefined}
                onResize={(_size, _id, _prev) => {
                    if (panelRef.current && collapsible) {
                        setCollapsed(panelRef.current.isCollapsed());
                    }
                }}
            >
                <div className={className}>
                    {title && (
                        <div className="report-engine__panel-title">
                            {!collapsed && <span>{title}</span>}
                            {collapsible && (
                                <Button
                                    type="text"
                                    size="small"
                                    icon={getIcon()}
                                    onClick={toggle}
                                />
                            )}
                        </div>
                    )}
                    {!collapsed && children}
                </div>
            </ResizablePanel>
        </>
    );
};

export default Panel;
