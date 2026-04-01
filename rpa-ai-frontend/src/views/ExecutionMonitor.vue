<template>
  <div class="monitor-container">
    <!-- 统计面板 -->
    <div class="stats-grid">
      <div class="stat-card tech-card" v-for="(stat, index) in stats" :key="index">
        <div class="stat-icon" :style="{ color: stat.color }">
          <el-icon size="40"><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
        <div class="stat-glow" :style="{ background: stat.color }"></div>
      </div>
    </div>

    <!-- 控制栏 -->
    <div class="control-bar tech-card">
      <div class="filter-group">
        <el-radio-group v-model="filterStatus" size="large" @change="handleFilterChange">
          <el-radio-button label="all">全部日志</el-radio-button>
          <el-radio-button label="success">成功</el-radio-button>
          <el-radio-button label="fail">失败</el-radio-button>
        </el-radio-group>
        
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="large"
          style="margin-left: 20px; width: 300px;"
          @change="handleDateChange"
        />
      </div>
      
      <div class="action-group">
        <el-button 
          type="primary" 
          size="large" 
          class="tech-btn"
          :loading="loading"
          @click="refreshLogs"
        >
          <el-icon><Refresh /></el-icon>
          刷新数据
        </el-button>
        <el-button 
          size="large"
          @click="exportLogs"
        >
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </div>
    </div>

    <!-- 日志表格 -->
    <div class="logs-table tech-card">
      <el-table 
        :data="paginatedLogs" 
        v-loading="loading"
        style="width: 100%"
        :row-class-name="tableRowClassName"
        @row-click="showDetail"
      >
        <el-table-column type="index" width="60" align="center">
          <template #default="{ $index }">
            <span class="index-num">{{ (currentPage - 1) * pageSize + $index + 1 }}</span>
          </template>
        </el-table-column>
        
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <div class="status-badge" :class="row.success ? 'success' : 'fail'">
              <el-icon v-if="row.success"><Check /></el-icon>
              <el-icon v-else><Close /></el-icon>
              {{ row.success ? '成功' : '失败' }}
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="taskName" label="任务名称" min-width="180">
          <template #default="{ row }">
            <div class="task-name">{{ row.taskName }}</div>
            <div class="task-desc">{{ row.naturalLanguage }}</div>
          </template>
        </el-table-column>
        
        <el-table-column label="执行进度" width="200">
          <template #default="{ row }">
            <div class="progress-info">
              <el-progress 
                :percentage="Math.round((row.completedSteps / row.totalSteps) * 100)" 
                :status="row.success ? 'success' : 'exception'"
                :stroke-width="8"
              />
              <span class="step-text">{{ row.completedSteps }}/{{ row.totalSteps }} 步</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="耗时" width="120" align="center">
          <template #default="{ row }">
            <span class="duration">{{ formatDuration(row.durationMs) }}</span>
          </template>
        </el-table-column>
        
        <el-table-column label="执行时间" width="180" align="center">
          <template #default="{ row }">
            <div class="time-info">
              <div class="date">{{ formatDate(row.startTime) }}</div>
              <div class="time">{{ formatTime(row.startTime) }}</div>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button 
              type="primary" 
              link
              @click.stop="showDetail(row)"
            >
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredLogs.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="`任务执行详情 #${selectedLog?.id}`"
      size="60%"
      :destroy-on-close="true"
      class="tech-drawer"
    >
      <div v-if="selectedLog" class="log-detail">
        <!-- 执行概览 -->
        <div class="detail-section">
          <h3 class="section-title">
            <el-icon><InfoFilled /></el-icon>
            执行概览
          </h3>
          <div class="info-grid">
            <div class="info-item">
              <span class="label">任务ID</span>
              <span class="value">{{ selectedLog.taskId }}</span>
            </div>
            <div class="info-item">
              <span class="label">执行状态</span>
              <span class="value" :class="selectedLog.success ? 'text-success' : 'text-danger'">
                {{ selectedLog.success ? '执行成功' : '执行失败' }}
              </span>
            </div>
            <div class="info-item">
              <span class="label">总耗时</span>
              <span class="value">{{ formatDuration(selectedLog.durationMs) }}</span>
            </div>
            <div class="info-item">
              <span class="label">完成步骤</span>
              <span class="value">{{ selectedLog.completedSteps }} / {{ selectedLog.totalSteps }}</span>
            </div>
          </div>
        </div>

        <!-- 步骤详情 -->
        <div class="detail-section">
          <h3 class="section-title">
            <el-icon><List /></el-icon>
            步骤执行详情
          </h3>
          <el-timeline>
            <el-timeline-item
              v-for="(step, index) in selectedLog.stepLogs"
              :key="index"
              :type="step.success ? 'success' : 'danger'"
              :icon="step.success ? Check : Close"
              :timestamp="formatDuration(step.executionTimeMs)"
            >
              <div class="step-detail-item">
                <div class="step-header">
                  <span class="step-num">步骤 {{ step.stepId }}</span>
                  <span class="step-action">{{ step.action }}</span>
                </div>
                <div class="step-target" v-if="step.target">
                  <el-tag size="small" effect="plain">目标: {{ step.target }}</el-tag>
                </div>
                <div class="step-message" v-if="step.message">
                  {{ step.message }}
                </div>
                <div class="step-error" v-if="step.errorMessage">
                  <el-icon><Warning /></el-icon>
                  {{ step.errorMessage }}
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- 错误信息 -->
        <div v-if="selectedLog.errorMessage" class="detail-section error-section">
          <h3 class="section-title">
            <el-icon><Warning /></el-icon>
            错误信息
          </h3>
          <div class="error-box">
            {{ selectedLog.errorMessage }}
          </div>
        </div>

        <!-- 元数据 -->
        <div class="detail-section">
          <h3 class="section-title">
            <el-icon><DataAnalysis /></el-icon>
            执行环境
          </h3>
          <div class="metadata-tags">
            <el-tag 
              v-for="(value, key) in selectedLog.metadata" 
              :key="key"
              effect="dark"
              class="metadata-tag"
            >
              {{ key }}: {{ value }}
            </el-tag>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { 
  Refresh, 
  Download, 
  Check, 
  Close, 
  InfoFilled, 
  List, 
  Warning,
  DataAnalysis,
  SuccessFilled,
  CircleCloseFilled,
  Timer,
  TrendCharts
} from '@element-plus/icons-vue'

// 数据状态
const logs = ref([])
const loading = ref(false)
const filterStatus = ref('all')
const dateRange = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const detailVisible = ref(false)
const selectedLog = ref(null)

// 统计
const stats = computed(() => [
  { 
    label: '总执行次数', 
    value: logs.value.length, 
    icon: TrendCharts, 
    color: '#00d4ff' 
  },
  { 
    label: '成功次数', 
    value: logs.value.filter(l => l.success).length, 
    icon: SuccessFilled, 
    color: '#00ff88' 
  },
  { 
    label: '失败次数', 
    value: logs.value.filter(l => !l.success).length, 
    icon: CircleCloseFilled, 
    color: '#ff3860' 
  },
  { 
    label: '平均耗时', 
    value: avgDuration.value, 
    icon: Timer, 
    color: '#ffaa00' 
  }
])

const avgDuration = computed(() => {
  if (logs.value.length === 0) return '0s'
  const avg = logs.value.reduce((sum, log) => sum + (log.durationMs || 0), 0) / logs.value.length
  return formatDuration(Math.round(avg))
})

// 过滤后的日志
const filteredLogs = computed(() => {
  let result = logs.value
  
  // 状态过滤
  if (filterStatus.value === 'success') {
    result = result.filter(l => l.success)
  } else if (filterStatus.value === 'fail') {
    result = result.filter(l => !l.success)
  }
  
  // 日期过滤
  if (dateRange.value && dateRange.value.length === 2) {
    const start = new Date(dateRange.value[0]).getTime()
    const end = new Date(dateRange.value[1]).getTime()
    result = result.filter(l => {
      const logTime = new Date(l.startTime).getTime()
      return logTime >= start && logTime <= end
    })
  }
  
  return result
})

// 分页后的数据
const paginatedLogs = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredLogs.value.slice(start, end)
})

// 获取所有日志（不分页，前端分页）
const refreshLogs = async () => {
  loading.value = true
  try {
    // 获取大量数据，后端支持 limit 参数
    const res = await fetch('/api/logs/recent?limit=1000')
    if (!res.ok) throw new Error('获取日志失败')
    const data = await res.json()
    logs.value = data || []
    ElMessage.success(`已加载 ${logs.value.length} 条日志记录`)
  } catch (error) {
    ElMessage.error('加载日志失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 表格行样式
const tableRowClassName = ({ row }) => {
  return row.success ? 'success-row' : 'fail-row'
}

// 显示详情
const showDetail = (row) => {
  selectedLog.value = row
  detailVisible.value = true
}

// 分页处理
const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
}

const handleCurrentChange = (val) => {
  currentPage.value = val
}

const handleFilterChange = () => {
  currentPage.value = 1
}

const handleDateChange = () => {
  currentPage.value = 1
}

// 导出日志
const exportLogs = () => {
  const dataStr = JSON.stringify(filteredLogs.value, null, 2)
  const blob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `execution_logs_${new Date().toISOString().split('T')[0]}.json`
  link.click()
  ElMessage.success('日志已导出')
}

// 格式化
const formatDuration = (ms) => {
  if (!ms) return '0s'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  const mins = Math.floor(ms / 60000)
  const secs = ((ms % 60000) / 1000).toFixed(0)
  return `${mins}分${secs}秒`
}

const formatDate = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleDateString('zh-CN')
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleTimeString('zh-CN')
}

onMounted(() => {
  refreshLogs()
})
</script>

<style scoped>
.monitor-container {
  padding-bottom: 40px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 30px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 25px;
  position: relative;
  overflow: hidden;
  cursor: pointer;
}

.stat-icon {
  width: 60px;
  height: 60px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20px;
  filter: drop-shadow(0 0 10px currentColor);
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  margin-bottom: 5px;
  text-shadow: 0 0 20px currentColor;
}

.stat-label {
  color: var(--tech-text-secondary);
  font-size: 14px;
}

.stat-glow {
  position: absolute;
  right: 0;
  top: 0;
  width: 100px;
  height: 100%;
  opacity: 0.1;
  filter: blur(40px);
}

.control-bar {
  padding: 20px;
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 15px;
}

.filter-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

:deep(.el-radio-button__inner) {
  background: rgba(0, 212, 255, 0.1);
  border-color: rgba(0, 212, 255, 0.3);
  color: var(--tech-text);
}

:deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--tech-primary);
  border-color: var(--tech-primary);
  color: #000;
  box-shadow: var(--tech-glow);
}

.logs-table {
  padding: 20px;
  min-height: 500px;
}

:deep(.el-table) {
  background: transparent;
}

:deep(.el-table__header) {
  background: rgba(0, 212, 255, 0.1);
}

:deep(.el-table th) {
  background: transparent;
  color: var(--tech-primary);
  font-weight: 600;
  border-bottom: 1px solid rgba(0, 212, 255, 0.2);
}

:deep(.el-table td) {
  background: transparent;
  color: var(--tech-text);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

:deep(.el-table__row:hover > td) {
  background: rgba(0, 212, 255, 0.05) !important;
}

:deep(.success-row) {
  background: rgba(0, 255, 136, 0.02);
}

:deep(.fail-row) {
  background: rgba(255, 56, 96, 0.02);
}

.index-num {
  color: var(--tech-text-secondary);
  font-family: monospace;
  font-size: 12px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

.status-badge.success {
  background: rgba(0, 255, 136, 0.2);
  color: var(--tech-success);
  border: 1px solid rgba(0, 255, 136, 0.3);
}

.status-badge.fail {
  background: rgba(255, 56, 96, 0.2);
  color: var(--tech-danger);
  border: 1px solid rgba(255, 56, 96, 0.3);
}

.task-name {
  font-weight: 600;
  color: #fff;
  margin-bottom: 5px;
}

.task-desc {
  font-size: 12px;
  color: var(--tech-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-info {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.step-text {
  font-size: 12px;
  color: var(--tech-text-secondary);
  text-align: right;
}

.duration {
  font-family: monospace;
  color: var(--tech-primary);
  font-weight: 600;
}

.time-info {
  text-align: center;
}

.time-info .date {
  font-size: 12px;
  color: var(--tech-text-secondary);
}

.time-info .time {
  font-size: 13px;
  color: var(--tech-text);
  font-family: monospace;
}

.pagination-wrapper {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  justify-content: center;
}

:deep(.el-pagination .el-pager li) {
  background: rgba(0, 212, 255, 0.1);
  border: 1px solid rgba(0, 212, 255, 0.2);
  color: var(--tech-text);
}

:deep(.el-pagination .el-pager li.active) {
  background: var(--tech-primary);
  color: #000;
  box-shadow: var(--tech-glow);
}

/* 详情抽屉样式 */
:deep(.tech-drawer) {
  background: rgba(10, 14, 26, 0.98) !important;
}

:deep(.tech-drawer .el-drawer__header) {
  color: #fff;
  border-bottom: 1px solid rgba(0, 212, 255, 0.2);
  padding: 20px;
  font-size: 18px;
}

.log-detail {
  padding: 20px;
}

.detail-section {
  margin-bottom: 30px;
  padding: 20px;
  background: rgba(0, 212, 255, 0.03);
  border: 1px solid rgba(0, 212, 255, 0.1);
  border-radius: 8px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--tech-primary);
  margin-bottom: 20px;
  font-size: 16px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 15px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.info-item .label {
  color: var(--tech-text-secondary);
}

.info-item .value {
  color: var(--tech-text);
  font-weight: 600;
  font-family: monospace;
}

.text-success { color: var(--tech-success); }
.text-danger { color: var(--tech-danger); }

.step-detail-item {
  padding: 10px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 6px;
  margin-bottom: 10px;
}

.step-header {
  display: flex;
  gap: 15px;
  margin-bottom: 8px;
}

.step-num {
  color: var(--tech-primary);
  font-weight: 600;
}

.step-action {
  color: var(--tech-text-secondary);
  text-transform: uppercase;
  font-size: 12px;
}

.step-target {
  margin-bottom: 8px;
}

.step-message {
  color: var(--tech-text);
  font-size: 13px;
  margin-bottom: 5px;
}

.step-error {
  color: var(--tech-danger);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.error-section {
  border-color: var(--tech-danger);
  background: rgba(255, 56, 96, 0.05);
}

.error-box {
  padding: 15px;
  background: rgba(255, 56, 96, 0.1);
  border: 1px solid rgba(255, 56, 96, 0.3);
  border-radius: 6px;
  color: var(--tech-danger);
  font-family: monospace;
  font-size: 13px;
  line-height: 1.6;
}

.metadata-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.metadata-tag {
  background: rgba(0, 212, 255, 0.1);
  border: 1px solid rgba(0, 212, 255, 0.3);
  color: var(--tech-primary);
}
</style>