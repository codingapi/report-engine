/**
 * 测试 setup（report-univer）。
 * 仅注册 jest-dom matcher——本包测试不涉及 antd 与接口，故无需 matchMedia/ResizeObserver
 * polyfill 与 MSW。详见 report-frontend/TESTING.md。
 */
import '@testing-library/jest-dom';
