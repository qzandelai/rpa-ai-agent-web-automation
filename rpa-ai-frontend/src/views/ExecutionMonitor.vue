<template>
  <div class="monitor-container">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stats-card" :body-style="{ padding: '20px' }">
          <div class="stats-item">
            <div class="stats-icon blue">📊</div>
            <div class="stats-info">
              <div class="stats-value">{{ totalExecutions }}</div>
              <div class="stats-label">总执行次数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stats-card" :body-style="{ padding: '20px' }">
          <div class="stats-item">
            <div class="stats-icon green">✅</div>
            <div class="stats-info">
              <div class="stats-value">{{ successCount }}</div>
              <div class="stats-label">成功次数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stats-card" :body-style="{ padding: '20px' }">
          <div class="stats-item">
            <div class="stats-icon red">❌</div>
            <div class="stats-info">
              <div class="stats-value">{{ failCount }}</div>
              <div class="stats-label">失败次数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stats-card" :body-style="{ padding: '20px' }">
          <div class="stats-item">
            <div class="stats-icon orange">🎯</div>
            <div class="stats-info">
              <div class="stats-value">{{ successRate }}%</div>
              <div class="stats-label">成功率</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-row :gutter="20" class="quick-access-row">
      <el-col :span="12">
        <el-card class="quick-card" @click="openNeo4j">
          <div class="quick-item">
            <div class="quick-icon">🧠</div>
            <div class="quick-info">
              <div class="quick-title">Neo4j 知识图谱</div>
              <div class="quick-desc">查看异常案例和解决方案</div>
            </div>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="quick-card" @click="refreshLogs">
          <div class="quick-item">
            <div class="quick-icon">🔄</div>
            <div class="quick-info">
              <div class="quick-title">刷新日志</div>
              <div class="quick-desc">获取最新执行记录</div>
            </div>
            <el-icon class="quick-arrow"><Refresh /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 执行日志列表 -->
    <el-card class="logs-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">📋 执行日志记录 (MongoDB)</span>
          <div class="header-actions">
            <el-radio-group v-model="logFilter" size="small" @change="filterLogs">
              <el-radio-button label="all">全部</el-radio-button>
              <el-radio-button label="success">成功</el-radio-button>
              <el-radio-button label="fail">失败</el-radio-button>
            </el-radio-group>
            <el-button type="primary" size="small" @click="refreshLogs" :loading="loading">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="filteredLogs.length === 0" description="暂无执行日志" />

      <div v-else class="logs-list">
        <el-collapse v-model="activeLogs">
          <el-collapse-item
              v-for="log in filteredLogs"
              :key="log.id"
              :name="log.id"
              :class="['log-item', log.success ? 'log-success' : 'log-fail']"
          >
            <template #title>
              <div class="log-header">
                <div class="log-main">
                  <el-tag :type="log.success ? 'success' : 'danger'" size="small" effect="dark">
                    {{ log.success ? '✅ 成功' : '❌ 失败' }}
                  </el-tag>
                  <span class="log-task">任务 #{{ log.taskId }}</span>
                  <span class="log-name">{{ log.taskName }}</span>
                </div>
                <div class="log-meta">
                  <el-tag type="info" size="small">{{ log.completedSteps }}/{{ log.totalSteps }} 步</el-tag>
                  <span class="log-time">{{ formatTime(log.startTime) }}</span>
                  <span class="log-duration">{{ log.durationMs }}ms</span>
                </div>
              </div>
            </template>

            <div class="log-detail">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="任务描述" :span="2">
                  {{ log.naturalLanguage || '无描述' }}
                </el-descriptions-item>
                <el-descriptions-item label="执行时间">
                  {{ formatFullTime(log.startTime) }}
                </el-descriptions-item>
                <el-descriptions-item label="总耗时">
                  {{ log.durationMs }} 毫秒
                </el-descriptions-item>
                <el-descriptions-item label="完成步骤">
                  {{ log.completedSteps }} / {{ log.totalSteps }}
                </el-descriptions-item>
                <el-descriptions-item label="错误信息" :span="2" v-if="!log.success">
                  <el-alert type="error" :title="log.errorMessage" :closable="false" />
                </el-descriptions-item>
              </el-descriptions>

              <!-- 步骤详情 -->
              <div class="steps-detail" v-if="log.stepLogs && log.stepLogs.length > 0">
                <h4>📝 步骤执行详情</h4>
                <el-timeline>
                  <el-timeline-item
                      v-for="(step, index) in log.stepLogs"
                      :key="index"
                      :type="step.success ? 'success' : 'danger'"
                      :icon="step.success ? 'Check' : 'Close'"
                      :timestamp="step.executionTimeMs + 'ms'"
                  >
                    <div class="step-item">
                      <div class="step-header">
                        <strong>步骤 {{ step.stepId }}: {{ step.action }}</strong>
                        <el-tag :type="step.success ? 'success' : 'danger'" size="small">
                          {{ step.success ? '成功' : '失败' }}
                        </el-tag>
                      </div>
                      <div class="step-content">
                        <p><strong>目标:</strong> {{ step.target || '无' }}</p>
                        <p v-if="step.message"><strong>结果:</strong> {{ step.message }}</p>
                        <p v-if="step.errorMessage" class="error-text">
                          <strong>错误:</strong> {{ step.errorMessage }}
                        </p>
                      </div>
                    </div>
                  </el-timeline-item>
                </el-timeline>
              </div>

              <!-- 元数据 -->
              <div class="metadata-section" v-if="log.metadata">
                <h4>📊 执行环境</h4>
                <el-tag
                    v-for="(value, key) in log.metadata"
                    :key="key"
                    size="small"
                    class="metadata-tag"
                >
                  {{ key }}: {{ value }}
                </el-tag>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <!-- 分页 -->
      <div class="pagination-wrapper" v-if="filteredLogs.length > 0">
        <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[5, 10, 20, 50]"
            :total="filteredLogs.length"
            layout="total, sizes, prev, pager, next"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 知识图谱统计 -->
    <el-card class="kg-stats-card" v-if="kgStats">
      <template #header>
        <div class="card-header">
          <span class="card-title">🧠 知识图谱统计 (Neo4j)</span>
          <el-button type="primary" size="small" @click="openNeo4j">
            <el-icon><Link /></el-icon> 打开 Neo4j
          </el-button>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :span="8">
          <div class="kg-stat-item">
            <div class="kg-stat-value">{{ kgStats.exceptionCases || 0 }}</div>
            <div class="kg-stat-label">异常案例</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="kg-stat-item">
            <div class="kg-stat-value">{{ kgStats.elementPatterns || 0 }}</div>
            <div class="kg-stat-label">元素模式</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="kg-stat-item">
            <div class="kg-stat-value">{{ (kgStats.topSolutions || []).length }}</div>
            <div class="kg-stat-label">热门方案</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import {ref, onMounted, computed} from 'vue'
import {ElMessage} from 'element-plus'
import {Refresh, ArrowRight, Link, Check, Close} from '@element-plus/icons-vue'
import {getRecentLogsAPI, getKnowledgeGraphStatsAPI} from '@/api/taskApi'

// 数据
const logs = ref([])
const loading = ref(false)
const logFilter = ref('all')
const activeLogs = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const kgStats = ref(null)

// 统计
const totalExecutions = computed(() => logs.value.length)
const successCount = computed(() => logs.value.filter(l => l.success).length)
const failCount = computed(() => logs.value.filter(l => !l.success).length)
const successRate = computed(() => {
  if (logs.value.length === 0) return 0
  return Math.round((successCount.value / logs.value.length) * 100)
})

// 过滤后的日志
const filteredLogs = computed(() => {
  let result = logs.value
  if (logFilter.value === 'success') {
    result = result.filter(l => l.success)
  } else if (logFilter.value === 'fail') {
    result = result.filter(l => !l.success)
  }
  // 分页
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return result.slice(start, end)
})

// 加载日志
const refreshLogs = async () => {
  loading.value = true
  try {
    const data = await getRecentLogsAPI(50)
    logs.value = data || []
    ElMessage.success(`加载了 ${logs.value.length} 条日志`)
  } catch (error) {
    ElMessage.error('加载日志失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 加载知识图谱统计
const loadKGStats = async () => {
  try {
    kgStats.value = await getKnowledgeGraphStatsAPI()
  } catch (error) {
    console.error('加载知识图谱统计失败:', error)
  }
}

// 打开 Neo4j
const openNeo4j = () => {
  window.open('http://localhost:7474', '_blank')
  ElMessage.info('正在打开 Neo4j Browser...')
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleTimeString()
}

const formatFullTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString()
}

// 分页
const handleSizeChange = (val) => {
  pageSize.value = val
}

const handleCurrentChange = (val) => {
  currentPage.value = val
}

// 过滤
const filterLogs = () => {
  currentPage.value = 1
}

// 初始化
onMounted(() => {
  refreshLogs()
  loadKGStats()
})
</script>

<style scoped>
.monitor-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

.stats-row {
  margin-bottom: 20px;
}

.stats-card {
  cursor: default;
  transition: all 0.3s;
}

.stats-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.stats-item {
  display: flex;
  align-items: center;
}

.stats-icon {
  font-size: 40px;
  margin-right: 15px;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
}

.stats-icon.blue {
  background: #e6f7ff;
}

.stats-icon.green {
  background: #f6ffed;
}

.stats-icon.red {
  background: #fff1f0;
}

.stats-icon.orange {
  background: #fff7e6;
}

.stats-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
  line-height: 1;
}

.stats-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}

.quick-access-row {
  margin-bottom: 20px;
}

.quick-card {
  cursor: pointer;
  transition: all 0.3s;
}

.quick-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border-color: #409eff;
}

.quick-item {
  display: flex;
  align-items: center;
  padding: 10px;
}

.quick-icon {
  font-size: 32px;
  margin-right: 15px;
}

.quick-title {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.quick-desc {
  font-size: 13px;
  color: #909399;
  margin-top: 3px;
}

.quick-arrow {
  margin-left: auto;
  color: #c0c4cc;
  font-size: 20px;
}

.logs-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.log-item {
  margin-bottom: 10px;
  border-radius: 8px;
  overflow: hidden;
}

.log-success {
  border-left: 4px solid #67c23a;
}

.log-fail {
  border-left: 4px solid #f56c6c;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 20px;
}

.log-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.log-task {
  font-weight: bold;
  color: #606266;
}

.log-name {
  color: #909399;
  font-size: 13px;
}

.log-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.log-time {
  color: #909399;
  font-size: 13px;
}

.log-duration {
  color: #409eff;
  font-size: 13px;
  font-weight: bold;
}

.log-detail {
  padding: 15px;
  background: #fafafa;
  border-radius: 8px;
  margin-top: 10px;
}

.steps-detail {
  margin-top: 20px;
}

.steps-detail h4 {
  margin-bottom: 15px;
  color: #303133;
}

.step-item {
  background: #fff;
  padding: 10px;
  border-radius: 6px;
  margin-bottom: 10px;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.step-content {
  font-size: 13px;
  color: #606266;
}

.step-content p {
  margin: 5px 0;
}

.error-text {
  color: #f56c6c;
}

.metadata-section {
  margin-top: 20px;
}

.metadata-section h4 {
  margin-bottom: 10px;
  color: #303133;
}

.metadata-tag {
  margin: 5px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.kg-stats-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.kg-stats-card .card-title {
  color: white;
}

.kg-stat-item {
  text-align: center;
  padding: 20px;
}

.kg-stat-value {
  font-size: 36px;
  font-weight: bold;
  margin-bottom: 5px;
}

.kg-stat-label {
  font-size: 14px;
  opacity: 0.9;
}
</style>