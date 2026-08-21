(function() {
    var style = getComputedStyle(document.documentElement);
    var accent = style.getPropertyValue('--accent').trim();
    var accent2 = style.getPropertyValue('--accent2').trim();
    var ink = style.getPropertyValue('--ink').trim();
    var muted = style.getPropertyValue('--muted').trim();
    var rule = style.getPropertyValue('--rule').trim();
    var bg2 = style.getPropertyValue('--bg2').trim();

    // --- Chart: Code Distribution ---
    var chartCodeDist = echarts.init(document.getElementById('chart-code-dist'), null, { renderer: 'svg' });
    chartCodeDist.setOption({
        animation: false,
        tooltip: {
            trigger: 'item',
            appendToBody: true,
            formatter: '{b}: {c} 行 ({d}%)'
        },
        legend: {
            orient: 'vertical',
            right: '5%',
            top: 'center',
            textStyle: { color: ink, fontSize: 12 },
            itemGap: 12
        },
        series: [{
            name: '代码行数分布',
            type: 'pie',
            radius: ['40%', '70%'],
            center: ['35%', '50%'],
            avoidLabelOverlap: false,
            itemStyle: {
                borderRadius: 6,
                borderColor: bg2,
                borderWidth: 2
            },
            label: {
                show: false
            },
            emphasis: {
                label: {
                    show: true,
                    fontSize: 14,
                    fontWeight: 'bold',
                    color: ink
                }
            },
            labelLine: {
                show: false
            },
            data: [
                { value: 710, name: 'MainActivity.kt', itemStyle: { color: accent } },
                { value: 710, name: 'SmsReceiver.kt', itemStyle: { color: accent2 } },
                { value: 746, name: 'SmsForegroundService.kt', itemStyle: { color: '#4CAF50' } },
                { value: 76, name: 'BootReceiver.kt', itemStyle: { color: '#FF9800' } },
                { value: 81, name: 'NetworkChangeReceiver.kt', itemStyle: { color: '#9C27B0' } },
                { value: 142, name: 'LogStore.kt', itemStyle: { color: '#00BCD4' } },
                { value: 74, name: 'models.kt', itemStyle: { color: '#E91E63' } },
                { value: 73, name: 'Constants.kt', itemStyle: { color: '#795548' } }
            ]
        }]
    });
    window.addEventListener('resize', function() { chartCodeDist.resize(); });

    // --- Chart: Feature Complexity Radar ---
    var chartRadar = echarts.init(document.getElementById('chart-radar'), null, { renderer: 'svg' });
    chartRadar.setOption({
        animation: false,
        tooltip: {
            appendToBody: true
        },
        radar: {
            indicator: [
                { name: '短信接收', max: 100 },
                { name: '转发逻辑', max: 100 },
                { name: '重试机制', max: 100 },
                { name: '电量提醒', max: 100 },
                { name: 'UI 交互', max: 100 },
                { name: '配置管理', max: 100 },
                { name: '可靠性保障', max: 100 }
            ],
            axisName: {
                color: ink,
                fontSize: 12
            },
            splitArea: {
                areaStyle: {
                    color: [bg2, 'transparent']
                }
            },
            axisLine: {
                lineStyle: { color: rule }
            },
            splitLine: {
                lineStyle: { color: rule }
            }
        },
        series: [{
            name: '功能完整度',
            type: 'radar',
            data: [{
                value: [90, 95, 90, 85, 80, 75, 85],
                name: '当前实现',
                areaStyle: {
                    color: accent + '33'
                },
                lineStyle: {
                    color: accent,
                    width: 2
                },
                itemStyle: {
                    color: accent
                }
            }]
        }]
    });
    window.addEventListener('resize', function() { chartRadar.resize(); });

    // --- Chart: Retry Schedule ---
    var chartRetry = echarts.init(document.getElementById('chart-retry'), null, { renderer: 'svg' });
    chartRetry.setOption({
        animation: false,
        tooltip: {
            trigger: 'axis',
            appendToBody: true,
            formatter: function(params) {
                return '第' + params[0].name + '次重试<br/>延迟: ' + params[0].value + ' 秒';
            }
        },
        grid: {
            left: '8%',
            right: '5%',
            bottom: '10%',
            top: '15%'
        },
        xAxis: {
            type: 'category',
            data: ['1', '2', '3', '4'],
            name: '重试次数',
            nameTextStyle: { color: muted },
            axisLine: { lineStyle: { color: rule } },
            axisLabel: { color: ink }
        },
        yAxis: {
            type: 'value',
            name: '延迟时间(秒)',
            nameTextStyle: { color: muted },
            axisLine: { lineStyle: { color: rule } },
            axisLabel: { color: ink },
            splitLine: { lineStyle: { color: rule } }
        },
        series: [{
            data: [30, 60, 120, 300],
            type: 'bar',
            barWidth: '50%',
            itemStyle: {
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                    { offset: 0, color: accent },
                    { offset: 1, color: accent2 }
                ]),
                borderRadius: [4, 4, 0, 0]
            },
            label: {
                show: true,
                position: 'top',
                color: ink,
                formatter: '{c}s'
            }
        }]
    });
    window.addEventListener('resize', function() { chartRetry.resize(); });
})();
