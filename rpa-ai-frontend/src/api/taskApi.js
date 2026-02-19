// API基础配置
const API_BASE = '/api'

// 请求封装
const request = async (url, options = {}) => {
  try {
    const response = await fetch(url, {
      headers: {
        'Content-Type': 'application/json'
      },
      ...options
    })
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    
    // 根据响应类型解析
    const contentType = response.headers.get('content-type')
    if (contentType && contentType.includes('application/json')) {
      return await response.json()
    }
    return await response.text()
  } catch (error) {
    console.error('API请求失败:', error)
    throw error
  }
}

// 任务解析
export const parseTaskAPI = (naturalLanguage) => {
  return request(`${API_BASE}/tasks/parse`, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain' },
    body: naturalLanguage
  })
}

// 保存任务
export const saveTaskAPI = (taskData) => {
  return request(`${API_BASE}/tasks/save`, {
    method: 'POST',
    body: JSON.stringify(taskData)
  })
}

// 获取任务
export const getTaskAPI = (taskId) => {
  return request(`${API_BASE}/tasks/${taskId}`)
}

// 健康检查
export const healthCheckAPI = () => {
  return fetch(`${API_BASE}/tasks/health`).then(r => r.text())
}

// 测试AI
export const testAIAPI = (question) => {
  return fetch(`${API_BASE}/test/ai?question=${encodeURIComponent(question)}`).then(r => r.text())
}

// ✅ 新增：执行任务
export const executeTaskAPI = (taskId) => {
  return request(`${API_BASE}/execution/task/${taskId}`, {
    method: 'POST'
  })
}

// ✅ 新增：直接执行步骤（测试用）
export const executeStepsAPI = (steps) => {
  return request(`${API_BASE}/execution/steps`, {
    method: 'POST',
    body: JSON.stringify(steps)
  })
}

// ✅ 新增：关闭浏览器
export const closeBrowserAPI = () => {
  return request(`${API_BASE}/execution/close`, {
    method: 'POST'
  })
}