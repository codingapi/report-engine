import { Fragment, jsx, jsxs } from "react/jsx-runtime";
import { Button, Form, Modal, Popconfirm, Select, Space, Table } from "antd";
import { useState } from "react";
const JOIN_OPTIONS = [
    {
        label: 'INNER',
        value: 'INNER'
    },
    {
        label: 'LEFT',
        value: 'LEFT'
    },
    {
        label: 'RIGHT',
        value: 'RIGHT'
    },
    {
        label: 'FULL',
        value: 'FULL'
    }
];
function RelationEditor({ datasets, relationships, onChange, disabled }) {
    const [modalOpen, setModalOpen] = useState(false);
    const [editing, setEditing] = useState(null);
    const [form] = Form.useForm();
    const leftDatasetId = Form.useWatch([
        'left',
        'datasetId'
    ], form);
    const rightDatasetId = Form.useWatch([
        'right',
        'datasetId'
    ], form);
    const datasetOptions = datasets.map((d)=>({
            label: d.alias ?? d.id,
            value: d.id
        }));
    const fieldOptionsOf = (datasetId)=>{
        if (!datasetId) return [];
        const ds = datasets.find((d)=>d.id === datasetId);
        return (ds?.fields ?? []).map((f)=>({
                label: f.alias ?? f.name,
                value: f.name
            }));
    };
    const columns = [
        {
            title: '左侧',
            key: 'left',
            render: (_, r)=>{
                const ds = datasets.find((d)=>d.id === r.left.datasetId);
                return `${ds?.alias ?? r.left.datasetId}.${r.left.field}`;
            }
        },
        {
            title: 'JOIN',
            dataIndex: 'joinType',
            key: 'joinType',
            width: 90
        },
        {
            title: '右侧',
            key: 'right',
            render: (_, r)=>{
                const ds = datasets.find((d)=>d.id === r.right.datasetId);
                return `${ds?.alias ?? r.right.datasetId}.${r.right.field}`;
            }
        },
        {
            title: '操作',
            key: 'actions',
            width: 100,
            render: (_, r)=>/*#__PURE__*/ jsxs(Space, {
                    children: [
                        /*#__PURE__*/ jsx("a", {
                            onClick: ()=>{
                                setEditing(r);
                                form.setFieldsValue(r);
                                setModalOpen(true);
                            },
                            children: "编辑"
                        }),
                        /*#__PURE__*/ jsx(Popconfirm, {
                            title: "确认删除？",
                            onConfirm: ()=>onChange?.(relationships.filter((x)=>x.id !== r.id)),
                            children: /*#__PURE__*/ jsx("a", {
                                children: "删除"
                            })
                        })
                    ]
                })
        }
    ];
    const handleAdd = ()=>{
        setEditing(null);
        form.setFieldsValue({
            id: `rel-${Date.now()}`,
            left: {
                datasetId: datasets[0]?.id ?? '',
                field: ''
            },
            right: {
                datasetId: datasets[1]?.id ?? datasets[0]?.id ?? '',
                field: ''
            },
            joinType: 'INNER'
        });
        setModalOpen(true);
    };
    const handleOk = async ()=>{
        const values = await form.validateFields();
        const next = editing ? relationships.map((r)=>r.id === values.id ? values : r) : [
            ...relationships,
            values
        ];
        onChange?.(next);
        setModalOpen(false);
    };
    return /*#__PURE__*/ jsxs(Fragment, {
        children: [
            /*#__PURE__*/ jsx(Space, {
                style: {
                    marginBottom: 8
                },
                children: /*#__PURE__*/ jsx(Button, {
                    onClick: handleAdd,
                    disabled: disabled || datasets.length < 2,
                    children: "新建关系"
                })
            }),
            /*#__PURE__*/ jsx(Table, {
                rowKey: "id",
                columns: columns,
                dataSource: relationships,
                pagination: false,
                size: "small"
            }),
            /*#__PURE__*/ jsx(Modal, {
                title: editing ? '编辑关系' : '新建关系',
                open: modalOpen,
                onOk: handleOk,
                onCancel: ()=>setModalOpen(false),
                destroyOnClose: true,
                children: /*#__PURE__*/ jsxs(Form, {
                    form: form,
                    layout: "vertical",
                    children: [
                        /*#__PURE__*/ jsx(Form.Item, {
                            name: "id",
                            hidden: true,
                            children: /*#__PURE__*/ jsx("input", {})
                        }),
                        /*#__PURE__*/ jsx(Form.Item, {
                            label: "左侧数据集",
                            name: [
                                'left',
                                'datasetId'
                            ],
                            rules: [
                                {
                                    required: true
                                }
                            ],
                            children: /*#__PURE__*/ jsx(Select, {
                                options: datasetOptions
                            })
                        }),
                        /*#__PURE__*/ jsx(Form.Item, {
                            label: "左侧字段",
                            name: [
                                'left',
                                'field'
                            ],
                            rules: [
                                {
                                    required: true
                                }
                            ],
                            children: /*#__PURE__*/ jsx(Select, {
                                options: fieldOptionsOf(leftDatasetId)
                            })
                        }),
                        /*#__PURE__*/ jsx(Form.Item, {
                            label: "JOIN 类型",
                            name: "joinType",
                            rules: [
                                {
                                    required: true
                                }
                            ],
                            children: /*#__PURE__*/ jsx(Select, {
                                options: JOIN_OPTIONS
                            })
                        }),
                        /*#__PURE__*/ jsx(Form.Item, {
                            label: "右侧数据集",
                            name: [
                                'right',
                                'datasetId'
                            ],
                            rules: [
                                {
                                    required: true
                                }
                            ],
                            children: /*#__PURE__*/ jsx(Select, {
                                options: datasetOptions
                            })
                        }),
                        /*#__PURE__*/ jsx(Form.Item, {
                            label: "右侧字段",
                            name: [
                                'right',
                                'field'
                            ],
                            rules: [
                                {
                                    required: true
                                }
                            ],
                            children: /*#__PURE__*/ jsx(Select, {
                                options: fieldOptionsOf(rightDatasetId)
                            })
                        })
                    ]
                })
            })
        ]
    });
}
export default RelationEditor;
