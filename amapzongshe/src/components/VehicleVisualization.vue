<template>
  <div class="vehicle-visualization">
    <div class="header">
      <h2>车辆分析</h2>
      <div class="controls">
        <select v-model="timeRange" @change="updateCharts">
          <option value="day">日</option>
          <option value="week">周</option>
          <option value="month">月</option>
        </select>
        <button @click="fetchAllData" class="refresh-btn">🔄 刷新数据</button>
      </div>
    </div>
    
    <div class="charts-grid">
      <div class="chart-item">
        <h3>车辆状态分布</h3>
        <div ref="statusDistributionChart" class="chart"></div>
      </div>
      
      <div class="chart-item">
        <h3>车辆利用率</h3>
        <div ref="utilizationChart" class="chart"></div>
      </div>
      
      <div class="chart-item">
        <h3>等待时间分析</h3>
        <div ref="waitingTimeChart" class="chart"></div>
      </div>
      
      <div class="chart-item">
        <h3>空驶分析</h3>
        <div ref="emptyMileageChart" class="chart"></div>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'

export default {
  name: 'VehicleVisualization',
  data() {
    return {
      timeRange: 'day',
      charts: {},
      vehicleData: {
        totalTrucks: 0,
        statusDistribution: {},
        utilization: { overall: 0 },
        waitingTime: { totalWaitingMinutes: 0, totalWaitingHours: 0, averageWaitingMinutesPerTruck: 0 },
        emptyMileage: { daily: 0, weekly: 0, monthly: 0 }
      }
    }
  },
  mounted() {
    this.initCharts()
    this.fetchAllData()
    this.refreshInterval = setInterval(() => this.fetchAllData(), 5000)
  },
  beforeUnmount() {
    Object.values(this.charts).forEach(chart => chart.dispose())
    if (this.refreshInterval) clearInterval(this.refreshInterval)
  },
  methods: {
    async fetchAllData() {
      await Promise.all([
        this.fetchTruckStatistics(),
        this.fetchWaitingTime()
      ])
      this.updateCharts()
    },
    async fetchTruckStatistics() {
      try {
        const response = await fetch('http://localhost:8080/truck/statistics')
        const data = await response.json()
        
        if (data) {
          this.vehicleData.totalTrucks = data.totalTrucks || 0
          this.vehicleData.statusDistribution = data.statusDistribution || {}
          
          if (data.waitingTimeStats) {
            this.vehicleData.waitingTime = {
              totalWaitingMinutes: data.waitingTimeStats.totalWaitingMinutes || 0,
              totalWaitingHours: data.waitingTimeStats.totalWaitingHours || 0,
              averageWaitingMinutesPerTruck: data.waitingTimeStats.averageWaitingMinutesPerTruck || 0
            }
          }
          
          this.calculateUtilization()
        }
      } catch (error) {
        console.error('获取车辆统计数据失败:', error)
      }
    },
    async fetchWaitingTime() {
      try {
        const response = await fetch('http://localhost:8080/truck/waiting-time')
        const data = await response.json()
        
        if (data.success && data.data) {
          this.vehicleData.waitingTime = {
            totalWaitingMinutes: data.data.totalWaitingMinutes || 0,
            totalWaitingHours: data.data.totalWaitingHours || 0,
            averageWaitingMinutesPerTruck: data.data.averageWaitingMinutesPerTruck || 0
          }
        }
      } catch (error) {
        console.error('获取车辆等待时间数据失败:', error)
      }
    },
    calculateUtilization() {
      const statusCounts = this.vehicleData.statusDistribution
      let working = 0
      let total = 0
      
      for (const [status, count] of Object.entries(statusCounts)) {
        const c = parseInt(count) || 0
        total += c
        if (status === '运输中' || status === '取货中') {
          working += c
        }
      }
      
      this.vehicleData.utilization.overall = total > 0 ? Math.round((working / total) * 100) : 0
    },
    calculateEmptyMileage() {
      const activeCount = Object.entries(this.vehicleData.statusDistribution)
        .filter(([status]) => status === '运输中' || status === '取货中')
        .reduce((sum, [_, count]) => sum + parseInt(count), 0)
      
      const dailyBase = activeCount * 25
      this.vehicleData.emptyMileage = {
        daily: dailyBase,
        weekly: dailyBase * 7,
        monthly: dailyBase * 30
      }
    },
    initCharts() {
      this.charts.statusDistribution = echarts.init(this.$refs.statusDistributionChart)
      this.charts.utilization = echarts.init(this.$refs.utilizationChart)
      this.charts.waitingTime = echarts.init(this.$refs.waitingTimeChart)
      this.charts.emptyMileage = echarts.init(this.$refs.emptyMileageChart)
      
      this.updateCharts()
      
      window.addEventListener('resize', this.handleResize)
    },
    handleResize() {
      Object.values(this.charts).forEach(chart => chart.resize())
    },
    updateCharts() {
      this.updateStatusDistributionChart()
      this.updateUtilizationChart()
      this.updateWaitingTimeChart()
      this.updateEmptyMileageChart()
    },
    updateStatusDistributionChart() {
      const statusNames = ['空闲', '取货中', '运输中', '卸货中', '维修']
      const data = statusNames.map(status => parseInt(this.vehicleData.statusDistribution[status]) || 0)
      
      const option = {
        tooltip: { trigger: 'item', formatter: '{b}: {c}辆 ({d}%)' },
        legend: { bottom: '5%', left: 'center' },
        series: [{
          name: '车辆状态',
          type: 'pie',
          radius: ['40%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
          label: { show: false, position: 'center' },
          emphasis: {
            label: { show: true, fontSize: '18', fontWeight: 'bold' }
          },
          labelLine: { show: false },
          data: statusNames.map((name, index) => ({
            value: data[index],
            name: name,
            itemStyle: {
              color: ['#91cc75', '#5470c6', '#fac858', '#ee6666', '#73c0de'][index]
            }
          })).filter(item => item.value > 0)
        }]
      }
      this.charts.statusDistribution.setOption(option)
    },
    updateUtilizationChart() {
      const option = {
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: '8%', right: '10%', bottom: '8%', top: '18%', containLabel: true },
        xAxis: {
          type: 'category',
          data: ['整体利用率'],
          axisLabel: { fontSize: 14 }
        },
        yAxis: { 
          type: 'value', 
          min: 0, 
          max: 100, 
          axisLabel: { formatter: '{value}%', fontSize: 14 } 
        },
        series: [{
          name: '利用率',
          type: 'bar',
          barWidth: '50%',
          data: [this.vehicleData.utilization.overall],
          itemStyle: {
            color: '#5470c6',
            borderRadius: [6, 6, 0, 0]
          },
          label: {
            show: true,
            position: 'top',
            formatter: '{c}%',
            fontSize: 16,
            fontWeight: 'bold'
          }
        }]
      }
      this.charts.utilization.setOption(option, true)
    },
    updateWaitingTimeChart() {
      const option = {
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: '8%', right: '8%', bottom: '6%', top: '20%', containLabel: true },
        xAxis: {
          type: 'category',
          data: ['总等待（分钟）', '总等待（小时）', '平均每车（分钟）'],
          axisLabel: { fontSize: 11, rotate: 16, interval: 0 }
        },
        yAxis: { 
          type: 'value', 
          axisLabel: { formatter: '{value}', fontSize: 12 } 
        },
        series: [{
          name: '等待时间',
          type: 'bar',
          barWidth: '40%',
          data: [
            this.vehicleData.waitingTime.totalWaitingMinutes || 0,
            this.vehicleData.waitingTime.totalWaitingHours || 0,
            this.vehicleData.waitingTime.averageWaitingMinutesPerTruck || 0
          ],
          itemStyle: {
            color: '#91cc75',
            borderRadius: [4, 4, 0, 0]
          },
          label: {
            show: true,
            position: 'top',
            formatter: '{c}',
            fontSize: 12
          }
        }]
      }
      this.charts.waitingTime.setOption(option, true)
    },
    updateEmptyMileageChart() {
      this.calculateEmptyMileage()

      const data = this.vehicleData.emptyMileage
      // 时间范围选择决定展示的粒度：日=只看当日，周=日+周，月=全部
      const cats = ['日空驶', '周空驶', '月空驶']
      const vals = [data.daily, data.weekly, data.monthly]
      const n = this.timeRange === 'day' ? 1 : this.timeRange === 'week' ? 2 : 3

      const option = {
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          formatter: (params) => `${params[0].name}：${params[0].value} 公里`
        },
        grid: { left: '8%', right: '8%', bottom: '8%', top: '18%', containLabel: true },
        xAxis: {
          type: 'category',
          data: cats.slice(0, n),
          axisLabel: { fontSize: 12 }
        },
        yAxis: {
          type: 'value',
          axisLabel: { formatter: '{value} 公里', fontSize: 12 }
        },
        series: [{
          name: '空驶里程',
          type: 'bar',
          barWidth: '45%',
          data: vals.slice(0, n),
          itemStyle: {
            color: '#5470c6',
            borderRadius: [6, 6, 0, 0]
          },
          label: {
            show: true,
            position: 'top',
            formatter: '{c} 公里',
            fontSize: 12
          }
        }]
      }
      this.charts.emptyMileage.setOption(option, true)
    }
  }
}
</script>

<style scoped>
.vehicle-visualization {
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e0e0e0;
}

.header h2 {
  margin: 0;
  font-size: 24px;
  color: #333;
}

.controls {
  display: flex;
  gap: 15px;
  align-items: center;
}

.controls select {
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  background: white;
  cursor: pointer;
}

.refresh-btn {
  padding: 8px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  background: white;
  cursor: pointer;
  transition: all 0.3s;
}

.refresh-btn:hover {
  background: #f5f7fa;
  border-color: #c0c4cc;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.chart-item {
  background: #ffffff;
  border: 1px solid #eef2f7;
  padding: 16px 18px;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.05);
}

.chart-item h3 {
  margin: 0 0 8px 0;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  text-align: left;
  display: flex;
  align-items: center;
  gap: 7px;
}

.chart-item h3::before {
  content: '';
  width: 4px;
  height: 14px;
  border-radius: 2px;
  background: linear-gradient(180deg, #38bdf8, #6366f1);
}

.chart {
  width: 100%;
  height: 300px;
}

@media (max-width: 768px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
  
  .header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .controls {
    width: 100%;
  }
  
  .controls select {
    flex: 1;
  }
}
</style>