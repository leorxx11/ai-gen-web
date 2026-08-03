/**
 * 时间格式化工具
 */

// 将后端时间字符串格式化为本地日期时间，空值返回 '-'
export const formatDateTime = (time?: string) => {
  if (!time) {
    return '-'
  }
  return new Date(time).toLocaleString()
}
