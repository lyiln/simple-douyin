export function formatCount(value: number | null | undefined): string {
  const n = Math.max(0, Number(value || 0));
  if (n >= 100000000) return `${trimDecimal(n / 100000000)}亿`;
  if (n >= 10000) return `${trimDecimal(n / 10000)}万`;
  return String(n);
}

export function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const diff = Date.now() - date.getTime();
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;
  if (diff < hour) return `${Math.max(1, Math.floor(diff / minute))}分钟前`;
  if (diff < day) return `${Math.floor(diff / hour)}小时前`;
  if (diff < 7 * day) return `${Math.floor(diff / day)}天前`;
  return `${date.getMonth() + 1}月${date.getDate()}日`;
}

export function clampCaption(value: string): string {
  return value.length > 200 ? `${value.slice(0, 197)}...` : value;
}

function trimDecimal(value: number): string {
  return value >= 10 ? String(Math.floor(value)) : value.toFixed(1).replace(/\.0$/, "");
}
