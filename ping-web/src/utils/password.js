/**
 * 密码强度校验：与后端 PasswordPolicy 默认策略保持一致。
 * 规则：至少 8 位，且大写/小写/数字/特殊字符至少满足 3 类。
 */
export function passwordStrengthError(value) {
  if (!value) {
    return '请输入密码'
  }
  if (value.length < 8) {
    return '密码至少 8 位'
  }
  let categories = 0
  if (/[a-z]/.test(value)) categories++
  if (/[A-Z]/.test(value)) categories++
  if (/\d/.test(value)) categories++
  if (/[^A-Za-z0-9]/.test(value)) categories++
  if (categories < 3) {
    return '密码需包含大写字母、小写字母、数字、特殊字符中的至少 3 类'
  }
  return ''
}

export function passwordValidator(_rule, value, callback) {
  const error = passwordStrengthError(value)
  if (error) {
    callback(new Error(error))
  } else {
    callback()
  }
}
