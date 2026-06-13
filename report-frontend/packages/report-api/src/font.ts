import http from './http';
import type { FontItem } from '@coding-report/report-univer';

export async function fetchFonts(): Promise<FontItem[]> {
  const response = await http.get('/fonts/list');
  return (response.data as { list: FontItem[] }).list;
}
