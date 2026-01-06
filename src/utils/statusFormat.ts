// Implements 3.实时感知模块
export const formatRelative = (value?: string | number | Date | null) => {
  if (!value) {
    return '刚刚';
  }
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '刚刚';
  }
  const diff = Date.now() - date.getTime();
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;
  if (diff < minute) {
    return '刚刚';
  }
  if (diff < hour) {
    const minutes = Math.floor(diff / minute);
    return `${minutes} 分钟前`;
  }
  if (diff < day) {
    const hours = Math.floor(diff / hour);
    return `${hours} 小时前`;
  }
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const dayOfMonth = String(date.getDate()).padStart(2, '0');
  const hourText = String(date.getHours()).padStart(2, '0');
  const minuteText = String(date.getMinutes()).padStart(2, '0');
  return `${month}-${dayOfMonth} ${hourText}:${minuteText}`;
};

export const parseErrorMessage = (
  value: unknown,
  fallback = '操作失败，请稍后再试'
) => {
  if (value instanceof Error && value.message) {
    return value.message;
  }
  if (typeof value === 'string' && value.trim()) {
    return value;
  }
  return fallback;
};