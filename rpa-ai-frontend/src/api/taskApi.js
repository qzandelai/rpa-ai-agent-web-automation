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

// 执行任务
export const executeTaskAPI = (taskId) => {
  return request(`${API_BASE}/execution/task/${taskId}`, {
    method: 'POST'
  })
}

// 直接执行步骤（测试用）
export const executeStepsAPI = (steps) => {
  return request(`${API_BASE}/execution/steps`, {
    method: 'POST',
    body: JSON.stringify(steps)
  })
}

// 关闭浏览器
export const closeBrowserAPI = () => {
  return request(`${API_BASE}/execution/close`, {
    method: 'POST'
  })
}

// ✅ 新增：获取最近日志
export const getRecentLogsAPI = (limit = 20) => {
  return request(`${API_BASE}/logs/recent?limit=${limit}`)
}

// ✅ 新增：获取任务历史
export const getTaskLogsAPI = (taskId) => {
  return request(`${API_BASE}/logs/task/${taskId}`)
}

// ✅ 新增：获取执行统计
export const getExecutionStatsAPI = (taskId) => {
  return request(`${API_BASE}/logs/stats/${taskId}`)
}

// ✅ 新增：获取知识图谱统计
export const getKnowledgeGraphStatsAPI = () => {
  return request(`${API_BASE}/kg/stats`)
}