import { render, screen } from '@testing-library/react';
import ExploreTree from '@/components/explore-tree';
import type { ColumnInfo, DatasourceService, TableInfo } from '@/types';

function makeService(opts: {
  tables?: TableInfo[];
  columns?: ColumnInfo[];
}): DatasourceService {
  return {
    testConnection: async () => ({ ok: true }),
    exploreTables: async () => opts.tables ?? [],
    exploreColumns: async () => opts.columns ?? [],
  };
}

describe('ExploreTree', () => {
  it('未传 sourceId 时渲染空状态', () => {
    render(<ExploreTree />);
    expect(screen.getByText('请先选择数据源')).toBeInTheDocument();
  });

  it('service 未注入时渲染错误提示', async () => {
    render(<ExploreTree sourceId="src-1" />);
    expect(await screen.findByText('exploreTables 未注入')).toBeInTheDocument();
  });

  it('注入 service 后渲染表列表', async () => {
    const service = makeService({
      tables: [
        { name: 'users', comment: '用户表' },
        { name: 'orders' },
      ],
    });
    render(<ExploreTree sourceId="src-1" service={service} />);
    expect(await screen.findByText('用户表 (users)')).toBeInTheDocument();
    expect(screen.getByText('orders')).toBeInTheDocument();
  });

  it('表列表为空时渲染空状态', async () => {
    const service = makeService({ tables: [] });
    render(<ExploreTree sourceId="src-1" service={service} />);
    expect(await screen.findByText('无可用表')).toBeInTheDocument();
  });
});
