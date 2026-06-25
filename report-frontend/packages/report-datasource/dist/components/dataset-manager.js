import { Fragment, jsx, jsxs } from "react/jsx-runtime";
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table } from "antd";
import { useState } from "react";
function DatasetManager({ datasets, dataSources, onChange }) {
    const [modalOpen, setModalOpen] = useState(false);
    const [editing, setEditing] = useState(null);
    const [form] = Form.useForm();
    const dataSourceMap = new Map(dataSources.map((d)=>[
            d.id,
            d
        ]));
    const columns = [
        {
            title: '别名',
            dataIndex: 'alias',
            key: 'alias',
            render: (_, r)=>r.alias ?? r.id
        },
        {
            title: '类型',
            key: 'kind',
            render: (_, r)=>'PHYSICAL' === r.kind ? '物理表' : 'UNION'
        },
        {
            title: '来源',
            key: 'source',
            render: (_, r)=>{
                if ('PHYSICAL' === r.kind) {
                    const ds = dataSourceMap.get(r.sourceId);
                    return `${ds?.name ?? r.sourceId}.${r.table}`;
                }
                return `${r.baseDatasetIds.length} 个数据集`;
            }
        },
        {
            title: '字段数',
            key: 'fields',
            render: (_, r)=>r.fields.length
        },
        {
            title: '操作',
            key: 'actions',
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
                            onConfirm: ()=>{
                                const next = datasets.filter((d)=>d.id !== r.id);
                                onChange?.(next);
                            },
                            children: /*#__PURE__*/ jsx("a", {
                                children: "删除"
                            })
                        })
                    ]
                })
        }
    ];
    const handleAdd = (kind)=>{
        const newId = `ds-${Date.now()}`;
        setEditing(null);
        if ('PHYSICAL' === kind) {
            const base = {
                id: newId,
                alias: '',
                sourceId: dataSources[0]?.id ?? '',
                table: '',
                fields: []
            };
            form.setFieldsValue({
                ...base,
                kind
            });
        } else form.setFieldsValue({
            id: newId,
            alias: '',
            baseDatasetIds: [],
            fields: [],
            kind
        });
        setModalOpen(true);
    };
    const handleOk = async ()=>{
        const values = await form.validateFields();
        const next = editing ? datasets.map((d)=>d.id === values.id ? values : d) : [
            ...datasets,
            values
        ];
        onChange?.(next);
        setModalOpen(false);
    };
    return /*#__PURE__*/ jsxs(Fragment, {
        children: [
            /*#__PURE__*/ jsxs(Space, {
                style: {
                    marginBottom: 8
                },
                children: [
                    /*#__PURE__*/ jsx(Button, {
                        onClick: ()=>handleAdd('PHYSICAL'),
                        children: "新建物理数据集"
                    }),
                    /*#__PURE__*/ jsx(Button, {
                        onClick: ()=>handleAdd('UNION'),
                        children: "新建 UNION 数据集"
                    })
                ]
            }),
            /*#__PURE__*/ jsx(Table, {
                rowKey: "id",
                columns: columns,
                dataSource: datasets,
                pagination: false,
                size: "small"
            }),
            /*#__PURE__*/ jsx(Modal, {
                title: editing ? '编辑数据集' : '新建数据集',
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
                            children: /*#__PURE__*/ jsx(Input, {})
                        }),
                        /*#__PURE__*/ jsx(Form.Item, {
                            name: "kind",
                            hidden: true,
                            children: /*#__PURE__*/ jsx(Input, {})
                        }),
                        /*#__PURE__*/ jsx(Form.Item, {
                            label: "别名",
                            name: "alias",
                            children: /*#__PURE__*/ jsx(Input, {})
                        }),
                        /*#__PURE__*/ jsx(Form.Item, {
                            noStyle: true,
                            shouldUpdate: (p, n)=>p?.kind !== n?.kind,
                            children: ({ getFieldValue })=>{
                                const kind = getFieldValue('kind');
                                if ('PHYSICAL' === kind) return /*#__PURE__*/ jsxs(Fragment, {
                                    children: [
                                        /*#__PURE__*/ jsx(Form.Item, {
                                            label: "数据源",
                                            name: "sourceId",
                                            rules: [
                                                {
                                                    required: true
                                                }
                                            ],
                                            children: /*#__PURE__*/ jsx(Select, {
                                                options: dataSources.map((d)=>({
                                                        label: d.name,
                                                        value: d.id
                                                    }))
                                            })
                                        }),
                                        /*#__PURE__*/ jsx(Form.Item, {
                                            label: "表名",
                                            name: "table",
                                            rules: [
                                                {
                                                    required: true
                                                }
                                            ],
                                            children: /*#__PURE__*/ jsx(Input, {})
                                        })
                                    ]
                                });
                                return /*#__PURE__*/ jsx(Form.Item, {
                                    label: "参与数据集",
                                    name: "baseDatasetIds",
                                    rules: [
                                        {
                                            required: true,
                                            type: 'array',
                                            min: 2
                                        }
                                    ],
                                    children: /*#__PURE__*/ jsx(Select, {
                                        mode: "multiple",
                                        options: datasets.filter((d)=>'PHYSICAL' === d.kind).map((d)=>({
                                                label: d.alias ?? d.id,
                                                value: d.id
                                            }))
                                    })
                                });
                            }
                        })
                    ]
                })
            })
        ]
    });
}
export default DatasetManager;
