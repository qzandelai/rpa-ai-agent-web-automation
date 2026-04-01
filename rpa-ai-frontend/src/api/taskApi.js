// API基础配置
const API_BASE = '/api'

// 延长超时时间到60秒（百度搜索需要等待页面跳转）
const request = async (url, options = {}) => {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 60000); // 60秒超时
  
  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers
      }
    });
    clearTimeout(timeoutId);
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return await response.json();
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('请求超时，请检查浏览器扩展是否正常运行');
    }
    throw error;
  }
}

// 任务解析
export const parseTaskAPI = (description, credentialsId) => {
  return request(`${API_BASE}/tasks/parse`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ description, credentialsId })
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

// ✅ 新增：取消任务API
export const cancelTaskAPI = (executionId) => {
  return request(`${API_BASE}/execution/task/${executionId}/cancel`, {
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

// 获取最近日志
export const getRecentLogsAPI = (limit = 20) => {
  return request(`${API_BASE}/logs/recent?limit=${limit}`)
}

// 获取任务历史
export const getTaskLogsAPI = (taskId) => {
  return request(`${API_BASE}/logs/task/${taskId}`)
}

// 获取执行统计
export const getExecutionStatsAPI = (taskId) => {
  return request(`${API_BASE}/logs/stats/${taskId}`)
}

// 获取知识图谱统计
export const getKnowledgeGraphStatsAPI = () => {
  return request(`${API_BASE}/kg/stats`)
}

// 获取知识图谱元素模式
export const getKnowledgeGraphPatternsAPI = () => {
  return request(`${API_BASE}/kg/patterns`)
}

// 获取知识图谱异常案例
export const getKnowledgeGraphCasesAPI = () => {
  return request(`${API_BASE}/kg/cases`)
}

// 删除知识图谱元素模式
export const deleteKnowledgeGraphPatternAPI = (id) => {
  return request(`${API_BASE}/kg/patterns/${id}`, {
    method: 'DELETE'
  })
}

// 获取任务提取的数据
export const getTaskDataAPI = (taskId) => {
  return request(`${API_BASE}/data/task/${taskId}`)
}

// 获取最近提取的数据
export const getRecentDataAPI = (limit = 20) => {
  return request(`${API_BASE}/data/recent?limit=${limit}`)
}

// 导出Excel
export const exportExcelAPI = (recordId, filename) => {
  const url = `${API_BASE}/data/export/excel/${recordId}?filename=${encodeURIComponent(filename || '')}`
  window.open(url, '_blank')
}

// 导出CSV
export const exportCsvAPI = (recordId, filename) => {
  const url = `${API_BASE}/data/export/csv/${recordId}?filename=${encodeURIComponent(filename || '')}`
  window.open(url, '_blank')
}