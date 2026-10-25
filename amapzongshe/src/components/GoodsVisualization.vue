<template>
  <div class="goods-visualization">
    <div class="header">
      <h2>货物分析</h2>
      <div class="controls">
        <select v-model="selectedType" @change="updateCharts">
          <option value="all">全部类型</option>
          <option value="general">普通货物</option>
          <option value="perishable">易腐货物</option>
          <option value="dangerous">危险品</option>
          <option value="bulk">散装货物</option>
        </select>
        <select v-model="timeRange" @change="updateCharts">
          <option value="day">日</option>
          <option value="week">周</option>
          <option value="month">月</option>
        </select>
      </div>
    </div>
    
    <div class="charts-grid">
      <div class="chart-item">
        <h3>货物总数</h3>
        <div ref="totalGoodsChart" class="chart"></div>
      </div>
      
      <div class="chart-item">
        <h3>货物状态分布</h3>
        <div ref="statusDistributionChart" class="chart"></div>
      </div>
      
      <div class="chart-item">
        <h3>货物类型分布</h3>
        <div ref="typeDistributionChart" class="chart"></div>
      </div>
      
      <div class="chart-item">
        <h3>优先级分布</h3>
        <div ref="priorityDistributionChart" class="chart"></div>
      </div>
      
      <div class="chart-item">
        <h3>需求区域分布</h3>
        <div ref="demandRegionChart" class="chart"></div>
      </div>
      
      <div class="chart-item">
        <h3>运输类型分布</h3>
        <div ref="transportTypeChart" class="chart"></div>
      </div>
      
      <div class="chart-item">
        <h3>等待类型分析</h3>
        <div ref="waitingTypeChart" class="chart"></div>
      </div>
      
      <div class="chart-item">
        <h3>损耗分析</h3>
        <div ref="lossAnalysisChart" class="chart"></div>
      </div>
    </div>
    
    <div class="data-tables">
      <div class="table-section">
        <h3>货物列表</h3>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>名称</th>
                <th>类型</th>
                <th>状态</th>
                <th>优先级</th>
                <th>重量(kg)</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="goods in goodsList" :key="goods.id">
                <td>{{ goods.id }}</td>
                <td>{{ goods.name }}</td>
                <td>{{ goods.type }}</td>
                <td>{{ goods.status }}</td>
                <td>{{ goods.priority }}</td>
                <td>{{ goods.weight }}</td>
                <td>{{ formatTime(goods.createTime) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      
      <div class="table-section">
        <h3>运输记录</h3>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>货物ID</th>
                <th>车辆ID</th>
                <th>运输类型</th>
                <th>运输区域</th>
                <th>开始时间</th>
                <th>结束时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="transport in transportList" :key="transport.id">
                <td>{{ transport.id }}</td>
                <td>{{ transport.goodsId }}</td>
                <td>{{ transport.truckId }}</td>
                <td>{{ transport.transportType }}</td>
                <td>{{ transport.transportRegion }}</td>
                <td>{{ formatTime(transport.startTime) }}</td>
                <td>{{ formatTime(transport.endTime) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'

export default {
  name: 'GoodsVisualization',
  data() {
    return {
      selectedType: 'all',
      timeRange: 'day',
      charts: {},
      goodsList: [],
      transportList: [],
      analysisData: {
        totalGoods: 0,
        totalDemand: 0,
        totalTransport: 0,
        totalWaiting: 0,
        totalLoss: 0,
        statusDistribution: {},
        typeDistribution: {},
        priorityDistribution: {},
        demandStatusDistribution: {},
        demandRegionDistribution: {},
        transportTypeDistribution: {},
        transportRegionDistribution: {},
        waitingTypeDistribution: {},
        lossTypeDistribution: {},
        totalLossValue: 0
      }
    }
  },
  mounted() {
    this.initCharts()
    this.fetchData()
  },
  beforeUnmount() {
    Object.values(this.charts).forEach(chart => chart.dispose())
    window.removeEventListener('resize', this.handleResize)
  },
  methods: {
    initCharts() {
      this.charts.totalGoods = echarts.init(this.$refs.totalGoodsChart)
      this.charts.statusDistribution = echarts.init(this.$refs.statusDistributionChart)
      this.charts.typeDistribution = echarts.init(this.$refs.typeDistributionChart)
      this.charts.priorityDistribution = echarts.init(this.$refs.priorityDistributionChart)
      this.charts.demandRegion = echarts.init(this.$refs.demandRegionChart)
      this.charts.transportType = echarts.init(this.$refs.transportTypeChart)
      this.charts.waitingType = echarts.init(this.$refs.waitingTypeChart)
      this.charts.lossAnalysis = echarts.init(this.$refs.lossAnalysisChart)
      
      window.addEventListener('resize', this.handleResize)
    },
    handleResize() {
      Object.values(this.charts).forEach(chart => chart.resize())
    },
    async fetchData() {
      try {
        const [analysisRes, goodsRes, transportRes] = await Promise.all([
          fetch('http://localhost:8080/goods/analysis').then(r => r.json()),
          fetch('http://localhost:8080/goods').then(r => r.json()),
          fetch('http://localhost:8080/goods/transport/all').then(r => r.json())
        ])
        
        this.analysisData = analysisRes
        this.goodsList = goodsRes.slice(0, 20)
        this.transportList = transportRes.slice(0, 20)
        
        this.updateCharts()
      } catch (error) {
        console.error('获取数据失败:', error)
        this.updateCharts()
      }
    },
    updateCharts() {
      this.updateTotalGoodsChart()
      this.updateStatusDistributionChart()
      this.updateTypeDistributionChart()
      this.updatePriorityDistributionChart()
      this.updateDemandRegionChart()
      this.updateTransportTypeChart()
      this.updateWaitingTypeChart()
      this.updateLossAnalysisChart()
    },
    updateTotalGoodsChart() {
      const totalGoods = this.analysisData.totalGoods || 0
      const totalDemand = this.analysisData.totalDemand || 0
      const totalTransport = this.analysisData.totalTransport || 0
      const totalWaiting = this.analysisData.totalWaiting || 0
      const totalLoss = this.analysisData.totalLoss || 0
      
      const option = {
        title: {
          text: `货物总数: ${totalGoods}`,
          left: 'center',
          top: 'center',
          textStyle: {
            fontSize: 24,
            fontWeight: 'bold'
          }
        },
        tooltip: {
          trigger: 'item'
        },
        series: [
          {
            type: 'pie',
            radius: ['60%', '80%'],
            avoidLabelOverlap: false,
            label: {
              show: false
            },
            data: [
              { value: totalDemand, name: '需求数' },
              { value: totalTransport, name: '运输数' },
              { value: totalWaiting, name: '等待数' },
              { value: totalLoss, name: '损耗数' }
            ]
          }
        ]
      }
      this.charts.totalGoods.setOption(option)
    },
    updateStatusDistributionChart() {
      const statusData = this.analysisData.statusDistribution || {}
      const data = Object.entries(statusData).map(([name, value]) => ({
        name,
        value
      }))
      
      const option = {
        tooltip: {
          trigger: 'item',
          formatter: '{b}: {c} ({d}%)'
        },
        legend: {
          bottom: '5%',
          left: 'center'
        },
        series: [
          {
            type: 'pie',
            radius: '50%',
            data: data.length > 0 ? data : [{ name: '暂无数据', value: 1 }],
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
      this.charts.statusDistribution.setOption(option)
    },
    updateTypeDistributionChart() {
      const typeData = this.analysisData.typeDistribution || {}
      const data = Object.entries(typeData).map(([name, value]) => ({
        name,
        value
      }))
      
      const option = {
        tooltip: {
          trigger: 'item',
          formatter: '{b}: {c} ({d}%)'
        },
        legend: {
          bottom: '5%',
          left: 'center'
        },
        series: [
          {
            type: 'pie',
            radius: '50%',
            data: data.length > 0 ? data : [{ name: '暂无数据', value: 1 }],
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
      this.charts.typeDistribution.setOption(option)
    },
    updatePriorityDistributionChart() {
      const priorityData = this.analysisData.priorityDistribution || {}
      const data = Object.entries(priorityData).map(([name, value]) => ({
        name,
        value
      }))
      
      const option = {
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          }
        },
        xAxis: {
          type: 'category',
          data: data.map(d => d.name)
        },
        yAxis: {
          type: 'value'
        },
        series: [
          {
            data: data.map(d => d.value),
            type: 'bar',
            itemStyle: {
              color: function(params) {
                const colors = ['#5470c6', '#91cc75', '#fac858', '#ee6666']
                return colors[params.dataIndex % colors.length]
              }
            }
          }
        ]
      }
      this.charts.priorityDistribution.setOption(option)
    },
    updateDemandRegionChart() {
      const regionData = this.analysisData.demandRegionDistribution || {}
      const data = Object.entries(regionData).map(([name, value]) => ({
        name,
        value
      }))
      
      const option = {
        tooltip: {
          trigger: 'item',
          formatter: '{b}: {c} ({d}%)'
        },
        legend: {
          bottom: '5%',
          left: 'center'
        },
        series: [
          {
            type: 'pie',
            radius: '50%',
            data: data.length > 0 ? data : [{ name: '暂无数据', value: 1 }],
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
      this.charts.demandRegion.setOption(option)
    },
    updateTransportTypeChart() {
      const transportData = this.analysisData.transportTypeDistribution || {}
      const data = Object.entries(transportData).map(([name, value]) => ({
        name,
        value
      }))
      
      const option = {
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          }
        },
        xAxis: {
          type: 'category',
          data: data.map(d => d.name)
        },
        yAxis: {
          type: 'value'
        },
        series: [
          {
            data: data.map(d => d.value),
            type: 'bar',
            itemStyle: {
              color: '#73c0de'
            }
          }
        ]
      }
      this.charts.transportType.setOption(option)
    },
    updateWaitingTypeChart() {
      const waitingData = this.analysisData.waitingTypeDistribution || {}
      const data = Object.entries(waitingData).map(([name, value]) => ({
        name,
        value
      }))
      
      const option = {
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          }
        },
        xAxis: {
          type: 'category',
          data: data.map(d => d.name),
          axisLabel: {
            rotate: 30
          }
        },
        yAxis: {
          type: 'value'
        },
        series: [
          {
            data: data.map(d => d.value),
            type: 'bar',
            itemStyle: {
              color: '#fc8452'
            }
          }
        ]
      }
      this.charts.waitingType.setOption(option)
    },
    updateLossAnalysisChart() {
      const lossData = this.analysisData.lossTypeDistribution || {}
      const totalLossValue = this.analysisData.totalLossValue || 0
      const data = Object.entries(lossData).map(([name, value]) => ({
        name,
        value
      }))
      
      const option = {
        title: {
          text: `总损耗价值: ${totalLossValue.toFixed(2)} 元`,
          left: 'center',
          top: 0,
          textStyle: { fontSize: 12, fontWeight: 500, color: '#b45309' }
        },
        tooltip: {
          trigger: 'item',
          formatter: '{b}: {c}次'
        },
        legend: {
          bottom: '5%',
          left: 'center'
        },
        series: [
          {
            type: 'pie',
            radius: '50%',
            data: data.length > 0 ? data : [{ name: '暂无损耗', value: 0 }],
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
      this.charts.lossAnalysis.setOption(option)
    },
    formatTime(time) {
      if (!time) return '-'
      if (Array.isArray(time)) {
        const [year, month, day, hour = 0, minute = 0, second = 0] = time
        return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`
      }
      return time
    }
  }
}
</script>

<style scoped>
.goods-visualization {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 15px 20px;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.header h2 {
  margin: 0;
  color: #303133;
}

.controls {
  display: flex;
  gap: 15px;
}

.controls select {
  padding: 8px 15px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background-color: white;
  cursor: pointer;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 20px;
}

.chart-item {
  background-color: white;
  border-radius: 8px;
  padding: 15px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.chart-item h3 {
  margin: 0 0 10px 0;
  color: #606266;
  font-size: 14px;
}

.chart {
  height: 250px;
}

.data-tables {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.table-section {
  background-color: white;
  border-radius: 8px;
  padding: 15px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.table-section h3 {
  margin: 0 0 15px 0;
  color: #303133;
}

.table-container {
  max-height: 300px;
  overflow-y: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th, td {
  padding: 10px;
  text-align: left;
  border-bottom: 1px solid #ebeef5;
}

th {
  background-color: #f5f7fa;
  color: #606266;
  font-weight: 500;
}

td {
  color: #303133;
}

tr:hover {
  background-color: #f5f7fa;
}

@media (max-width: 1400px) {
  .charts-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
  
  .data-tables {
    grid-template-columns: 1fr;
  }
}
</style>
