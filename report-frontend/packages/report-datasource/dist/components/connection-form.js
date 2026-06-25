import { jsx, jsxs } from "react/jsx-runtime";
import { App, Button, Form, Input, Select } from "antd";
import { useState } from "react";
const DATASOURCE_TYPE_OPTIONS = [
    {
        label: 'CSV',
        value: 'CSV'
    },
    {
        label: 'JSON',
        value: 'JSON'
    },
    {
        label: 'DB',
        value: 'DB'
    },
    {
        label: 'API',
        value: 'API'
    },
    {
        label: 'EXCEL',
        value: 'EXCEL'
    }
];
function ConnectionForm({ value, onChange, service, testResult, onTestResultChange, disabled }) {
    const { message } = App.useApp();
    const [testing, setTesting] = useState(false);
    const handleValuesChange = (_, all)=>{
        onChange?.(all);
    };
    const handleTest = async ()=>{
        if (!service?.testConnection || !value) return void onTestResultChange?.(null);
        setTesting(true);
        try {
            const result = await service.testConnection({
                type: value.type,
                url: value.url,
                username: value.username,
                password: value.password,
                options: value.options
            });
            onTestResultChange?.(result);
            if (result.ok) message.success('连接成功');
            else message.error(result.message ?? '连接失败');
        } catch (e) {
            const msg = e instanceof Error ? e.message : String(e);
            onTestResultChange?.({
                ok: false,
                message: msg
            });
            message.error(msg);
        } finally{
            setTesting(false);
        }
    };
    const merged = value ?? {};
    const canTest = !!service?.testConnection && !!value?.type && !disabled && !testing;
    return /*#__PURE__*/ jsxs(Form, {
        layout: "vertical",
        initialValues: merged,
        onValuesChange: handleValuesChange,
        disabled: disabled,
        children: [
            /*#__PURE__*/ jsx(Form.Item, {
                label: "名称",
                name: "name",
                rules: [
                    {
                        required: true,
                        message: '请输入名称'
                    }
                ],
                children: /*#__PURE__*/ jsx(Input, {
                    placeholder: "数据源名称"
                })
            }),
            /*#__PURE__*/ jsx(Form.Item, {
                label: "类型",
                name: "type",
                rules: [
                    {
                        required: true,
                        message: '请选择类型'
                    }
                ],
                children: /*#__PURE__*/ jsx(Select, {
                    options: DATASOURCE_TYPE_OPTIONS,
                    placeholder: "选择数据源类型"
                })
            }),
            /*#__PURE__*/ jsx(Form.Item, {
                label: "连接 URL",
                name: "url",
                children: /*#__PURE__*/ jsx(Input, {
                    placeholder: "JDBC URL / CSV 路径 / JSON URL / API endpoint"
                })
            }),
            /*#__PURE__*/ jsx(Form.Item, {
                label: "用户名",
                name: "username",
                children: /*#__PURE__*/ jsx(Input, {
                    autoComplete: "off"
                })
            }),
            /*#__PURE__*/ jsx(Form.Item, {
                label: "密码 / Token",
                name: "password",
                children: /*#__PURE__*/ jsx(Input.Password, {
                    autoComplete: "new-password"
                })
            }),
            /*#__PURE__*/ jsx(Form.Item, {
                label: "额外选项 (JSON)",
                name: "options",
                children: /*#__PURE__*/ jsx(Input.TextArea, {
                    rows: 2,
                    placeholder: '{"key":"value"}'
                })
            }),
            /*#__PURE__*/ jsxs(Form.Item, {
                children: [
                    /*#__PURE__*/ jsx(Button, {
                        loading: testing,
                        disabled: !canTest,
                        onClick: handleTest,
                        children: "测试连接"
                    }),
                    testResult ? /*#__PURE__*/ jsx("span", {
                        style: {
                            marginLeft: 12,
                            color: testResult.ok ? 'green' : 'red'
                        },
                        children: testResult.ok ? '连接成功' : `失败：${testResult.message ?? ''}`
                    }) : null
                ]
            })
        ]
    });
}
export default ConnectionForm;
