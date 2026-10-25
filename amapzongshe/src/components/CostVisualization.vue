<template>
  <div class="cost-visualization">
    <h2>成本分析与可视化</h2>
    
    <div class="cost-summary">
      <div class="cost-card">
        <div class="cost-label">直接成本(A)</div>
        <div class="cost-value">{{ (costData.costA || 0).toFixed(2) }}</div>
      </div>
      <div class="cost-card">
        <div class="cost-label">运货快(B)</div>
        <div class="cost-value">{{ (costData.costB || 0).toFixed(2) }}</div>
      </div>
      <div class="cost-card">
        <div class="cost-label">效率+公平(C)</div>
        <div class="cost-value">{{ (costData.costC || 0).toFixed(2) }}</div>
      </div>
      <div class="cost-card">
        <div class="cost-label">平台(D)</div>
        <div class="cost-value">{{ (costData.costD || 0).toFixed(2) }}</div>
      </div>
      <div class="cost-card">
        <div class="cost-label">风险(E)</div>
        <div class="cost-value">{{ (costData.costE || 0).toFixed(2) }}</div>
      </div>
      <div class="cost-card">
        <div class="cost-label">大综合(F)</div>
        <div class="cost-value">{{ (costData.costF || 0).toFixed(2) }}</div>
      </div>
    </div>
    
    <div class="chart-container">
      <div class="chart-card">
        <h3>成本模型对比（A-F）</h3>
        <div ref="costComparisonChart" class="chart"></div>
      </div>
      <div class="chart-card">
        <h3>成本构成详情</h3>
        <div ref="costDetailsChart" class="chart"></div>
      </div>
    </div>
    
    <div class="cost-details">
      <h3>成本详情</h3>
      <div class="details-grid">
        <div class="detail-item" v-for="(value, key) in detailsData" :key="key">
          <span class="detail-label">{{ getDetailLabel(key) }}:</span>
          <span class="detail-value">{{ formatValue(value) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'

export default {
  name: 'CostVisualization',
  data() {
    return {
      costData: {
        costA: 0,
        costB: 0,
        costC: 0,
        costD: 0,
        costE: 0,
        costF: 0
      },
      detailsData: {},
      charts: {}
    }
  },
  mounted() {
    this.initCharts()
    this.fetchCostData()
    this.refreshInterval = setInterval(() => this.fetchCostData(), 5000) // 每5秒更新一次
  },
  beforeUnmount() {
    Object.values(this.charts).forEach(chart => chart.dispose())
    if (this.refreshInterval) clearInterval(this.refreshInterval)
  },
  methods: {
    async fetchCostData() {
      try {
        const response = await fetch('http://localhost:8080/truck/cost')
        const data = await response.json()
        
        if (data.success && data.data) {
          this.costData = data.data
          this.detailsData = data.data.details || {}
          this.updateCharts()
        }
      } catch (error) {
        console.error('获取成本数据失败:', error)
      }
    },
    initCharts() {
      this.charts.costComparison = echarts.init(this.$refs.costComparisonChart)
      this.charts.costDetails = echarts.init(this.$refs.costDetailsChart)
    },
    updateCharts() {
      this.updateCostComparisonChart()
      this.updateCostDetailsChart()
    },
    updateCostComparisonChart() {
      const option = {
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          }
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: ['直接成本(A)', '运货快(B)', '效率+公平(C)', '平台(D)', '风险(E)', '大综合(F)'],
          axisLabel: { fontSize: 11, interval: 0 }
        },
        yAxis: {
          type: 'value',
          axisLabel: {
            formatter: '{value}'
          }
        },
        series: [
          {
            name: '成本值',
            type: 'bar',
            data: [
              this.costData.costA || 0,
              this.costData.costB || 0,
              this.costData.costC || 0,
              this.costData.costD || 0,
              this.costData.costE || 0,
              this.costData.costF || 0
            ],
            barMinHeight: 5,
            label: {
              show: true,
              position: 'top',
              formatter: function(params) {
                return params.value > 0 ? params.value.toFixed(2) : ''
              },
              fontSize: 10
            },
            itemStyle: {
              color: function(params) {
                const colors = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272']
                return colors[params.dataIndex]
              }
            }
          }
        ]
      }
      this.charts.costComparison.setOption(option)
    },
    updateCostDetailsChart() {
      const option = {
        tooltip: {
          trigger: 'item'
        },
        legend: {
          orient: 'vertical',
          left: 'left'
        },
        series: [
          {
            name: '成本构成',
            type: 'pie',
            radius: '50%',
            data: [
              { value: this.costData.costA || 0, name: '直接成本(A)' },
              { value: this.costData.costB || 0, name: '运货快(B)' },
              { value: this.costData.costC || 0, name: '效率+公平(C)' },
              { value: this.costData.costD || 0, name: '平台(D)' },
              { value: this.costData.costE || 0, name: '风险(E)' }
            ],
            emphasis: {
              itemStyle: {
                shadowBlur: 10,
                shadowOffsetX: 0,
                shadowColor: 'rgba(0, 0, 0, 0.5)'
              }
            }
          }
        ]
      }
      this.charts.costDetails.setOption(option)
    },
    getCostLabel(key) {
      const labels = {
        costA: '直接成本(A)',
        costB: '运货快(B)',
        costC: '效率+公平(C)',
        costD: '平台(D)',
        costE: '风险(E)',
        costF: '大综合(F)'
      }
      return labels[key] || key
    },
    getDetailLabel(key) {
      const labels = {
        totalWaitingTime: '总等待时间(小时)',
        totalEmptyMileage: '总空驶里程(公里)',
        totalWeightTime: '总吨*等待时间',
        maxGoodsWaitingTime: '最慢货物等待时间(小时)',
        totalMileage: '总里程(公里)',
        totalTransportTime: '总运输时间(小时)',
        maxWaitingTime: '最长等待时间(小时)',
        totalCapacity: '总运能(吨)',
        totalActualCapacity: '总实际运能(吨)',
        minCapacityDifference: '最低运能差异(吨)',
        waitingTimeStd: '等待时间标准差',
        pickupTimeStd: '拿货时间标准差',
        averageDistance: '平均运距(公里)'
      }
      return labels[key] || key
    },
    formatValue(value) {
      if (typeof value === 'number') {
        return value.toFixed(2)
      }
      return value
    }
  }
}
</script>

<style scoped>
.cost-visualization {
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.cost-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 15px;
  margin-bottom: 30px;
}

.cost-card {
  background-color: white;
  padding: 15px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  text-align: center;
}

.cost-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 5px;
}

.cost-value {
  font-size: 20px;
  font-weight: bold;
  color: #409eff;
}

.chart-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}

.chart-card {
  background-color: #ffffff;
  border: 1px solid #eef2f7;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.05);
}

.chart-card h3 {
  margin: 0 0 8px 0;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 7px;
}

.chart-card h3::before {
  content: '';
  width: 4px;
  height: 14px;
  border-radius: 2px;
  background: linear-gradient(180deg, #38bdf8, #6366f1);
}

.chart {
  height: 360px;
  background-color: transparent;
  border-radius: 0;
  box-shadow: none;
  padding: 0;
}

.cost-details {
  background-color: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.details-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 15px;
  margin-top: 15px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.detail-label {
  color: #606266;
}

.detail-value {
  font-weight: 500;
  color: #303133;
}

@media (max-width: 768px) {
  .chart-container {
    grid-template-columns: 1fr;
  }
  
  .cost-summary {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>