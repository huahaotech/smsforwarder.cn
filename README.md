# 短信转发助手 (SmsForwarder.cn)

<div align="center">

![Version](https://img.shields.io/badge/version-2.8.2-blue.svg)
![Android](https://img.shields.io/badge/Android-5.0%2B-brightgreen.svg)
![License](https://img.shields.io/badge/license-MIT-orange.svg)
[![GitHub Stars](https://img.shields.io/github/stars/jinghong-me/smsforwarder.cn?style=social)](https://github.com/jinghong-me/smsforwarder.cn/stargazers)

轻量、稳定、开源的 Android 短信转发应用。支持企业微信、钉钉、飞书和自定义 Webhook 等多种转发渠道，支持关键词过滤、验证码提取、本机号码识别等功能。

[快速开始](#快速开始) • [功能特性](#功能特性) • [下载安装](#下载安装) • [使用指南](#使用指南)

</div>

---

## ✨ 功能特性

### 🎯 核心功能
- **多通道支持** - 企业微信、钉钉、飞书、通用 Webhook 四种转发通道
- **关键词过滤** - 灵活的关键词规则配置，空关键词转发全部
- **验证码提取** - 自动识别并突出显示短信验证码，方便复制
- **本机号码识别** - 双卡设备支持识别接收短信号码，转发时显示本机号码
- **自定义 SIM 号码** - 无法自动获取时支持手动输入本机号码
- **可配置消息格式** - 灵活配置是否显示本机号码、发送者号码、验证码
- **SIM 卡信息预览** - 前端显示 SIM 卡号码状态，提前预知能否获取
- **消息去重** - 5秒内相同内容自动去重，避免重复转发
- **智能重试** - 失败消息最多重试3次，指数退避策略（2s/4s/6s）
- **持久化存储** - 失败消息保存本地，应用重启或网络恢复自动重试
- **电量提醒** - 低电量/高电量提醒，可独立配置开关和阈值，通过配置的 Webhook 通道发送
- **充电提醒** - 设备开始或结束充电时发送提醒，可选择发送到指定通道
- **配置导入导出** - 支出导出和导入配置文件，以便于备份或者分享到其他设备。

### 🛡️ 可靠性设计
- **前台服务常驻** - 提高在厂商定制 ROM（如 HyperOS）上的存活率
- **网络状态监听** - 网络恢复自动触发失败消息重试
- **运行日志记录** - 完整日志保存，方便排查问题
- **电池优化引导** - 引导用户加入白名单，减少系统杀死概率

### 🎨 用户体验
- **Material Design 3** - 现代化 UI 设计
- **深色模式** - 自动适配系统主题
- **规则测试** - 在 App 内测试关键词规则
- **开机自启** - 设备启动后自动运行

---

## 📥 下载安装

### 最新版本
| 版本 | 说明 | 下载链接 |
|------|------|----------|
| **v2.8.2** | 增加充电提醒功能；增加防诈骗提示；增加导入和导出功能。 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.10** | 隐私合规整改：添加首次运行隐私政策弹窗（纯文本展示）；设置页面添加隐私政策查看入口和撤销同意功能；权限管理页面展示所有权限状态；优化隐私政策弹窗界面 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.9** | 隐私合规整改：优化权限申请时机，首次打开不再主动弹窗请求权限；用户点击开启服务时先显示权限说明对话框，引导用户到系统设置手动开启；移除直接系统权限请求弹窗 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.8** | 电量提醒消息添加SIM卡手机号标识，便于区分设备；修复飞书消息格式问题；低电量和高电量提醒可独立配置开关和阈值 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.7** | 修复SIM卡设置页面电话权限状态不实时更新问题；添加电话权限友好提示；添加开机自启动友好提示；更新隐私政策；新增电量提醒功能；优化APK大小；增强代码健壮性 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.6** | 修复首页权限状态实时更新问题；关于页面自动获取版本号；添加官方网址和备案号；删除版权信息中的版本号 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.5** | 更新签名证书，准备 APP 备案 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.7.2** | 优化更新对话框和关于对话框按钮样式，统一按钮大小和位置；修复双卡设备 SIM 卡识别 bug，SIM2 收到的短信现在会正确显示 SIM2 的本机号码；优化 subscriptionId 获取方式，提高兼容性 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.6.4** | 修复双卡设备 SIM 卡识别 bug，SIM2 收到的短信现在会正确显示 SIM2 的本机号码；优化 subscriptionId 获取方式，提高兼容性 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.6.1** | 更新关于对话框，添加版权信息和软件说明；调整菜单布局，避免功能重复 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.6.0** | 降低最低支持版本到 Android 5.0 (API 21)，支持更多设备 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| **v2.5.2** | 调整页面内容分配，首页包含服务开关和开机启动，设置页包含SIM卡和消息格式 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.5.1 | 修复自定义 SIM 号码显示逻辑问题，避免重复出现 SIM 卡 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.5.0 | 新增底部导航栏，将功能分为首页、关键词、通道、设置、日志5个标签页 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.4.1 | 优化自定义 SIM 号码功能，添加更清晰的使用提示 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.4.0 | 新增自定义 SIM 号码功能，无法自动获取时支持手动输入 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.3.0 | 新增消息格式配置选项、SIM 卡信息预览 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.2.0 | 新增本机号码识别，双卡设备支持显示接收短信号码 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.1.3 | 修复验证码识别错误，优化提取逻辑 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.1.2 | 调整验证码显示顺序，优化消息格式 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.1.0 | 验证码自动提取与突出显示 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.0.2 | 统一消息前缀格式 | [Releases](https://github.com/jinghong-me/smsforwarder.cn/releases) |
| v2.0.1 | 多关键词、多通道 | [v2.0.1](https://github.com/jinghong-me/smsforwarder.cn/releases/tag/v2.0.1) |
| v1.1 | 多关键词、单通道 | [v1.1](https://github.com/jinghong-me/smsforwarder.cn/releases/tag/1.1) |

---

## 🚀 快速开始

### 环境要求
- Android 5.0 (API 21) 及以上
- 需要短信接收权限、通知权限、网络权限

### 安装步骤
1. 下载 APK 并安装
2. 打开应用，阅读并同意隐私政策（首次运行显示）
3. 添加转发通道（企业微信/钉钉/飞书/Webhook）
4. 配置关键词规则
5. 开启转发服务（首次开启时会显示权限说明，引导到系统设置开启短信和通知权限）

### 权限说明
| 权限 | 用途 | 是否必需 |
|------|------|----------|
| 接收短信 | 监听设备收到的短信，用于执行转发功能 | ✅ 必需 |
| 通知权限 | 显示前台服务通知，让您知道服务正在运行 | ✅ 必需 |
| 读取手机状态 | 识别双卡手机的 SIM 卡信息和获取本机号码 | ❌ 可选 |
| 网络权限 | 转发到您配置的 Webhook | ✅ 必需 |
| 开机自启 | 设备启动后自动启动转发服务 | ❌ 可选 |
| 忽略电池优化 | 防止系统杀死后台服务 | ❌ 可选 |

### 隐私政策
- 应用首次运行时会显示完整隐私政策弹窗（纯文本展示）
- 用户可随时在设置页面查看隐私政策或撤销同意
- 所有数据仅存储在本地，不上传服务器

---

## 📖 使用指南

### 1. 添加转发通道
进入「转发通道管理」，点击「添加通道」：
- **企业微信**：填写 Webhook 地址
- **钉钉**：填写 Webhook 地址
- **飞书**：填写 Webhook 地址
- **通用 Webhook**：填写 HTTP(S) 地址

### 2. 配置关键词规则
进入「关键词配置」：
- 输入关键词（留空表示转发全部）
- 选择目标转发通道
- 点击「添加配置」

### 3. 开启服务
- 打开「转发服务」开关
- （可选）开启「开机自启动」
- 建议在系统设置中允许自启动和后台保活

### 4. 配置导入导出（二维码）
- **导出配置**：在设置页面点击「导出配置」，会生成包含所有配置的二维码，截图保存即可
- **导入配置**：在另一台设备的设置页面点击「扫码导入」，扫描保存的二维码即可完成配置迁移

---

## 🛠️ 开发者指南

### 项目结构
```
app/src/main/java/com/lanbing/smsforwarder/
├── MainActivity.kt              # Compose UI、规则测试、关于对话框、二维码导入导出
├── SmsReceiver.kt               # 短信接收、转发、去重、重试
├── SmsForegroundService.kt      # 前台服务与通知（包含电量监听）
├── BootReceiver.kt              # 开机启动处理
├── NetworkChangeReceiver.kt     # 网络状态监听
├── LogStore.kt                  # 日志存储
├── QrCodeUtil.kt                # 二维码生成工具
├── models.kt                    # 数据模型
└── Constants.kt                 # 常量定义
```

### 本地构建
```bash
# 克隆仓库
git clone https://github.com/jinghong-me/smsforwarder.cn.git
cd smsforwarder.cn

# 构建 Debug APK
./gradlew clean :app:assembleDebug

# 构建 Release APK（需要签名配置）
./gradlew clean :app:assembleRelease
```

### 技术栈
- **语言**：Kotlin
- **UI**：Jetpack Compose + Material Design 3
- **网络**：OkHttp
- **二维码**：ZXing
- **最低支持**：Android 5.0 (API 21)
- **目标版本**：Android 14 (API 34)

---

## 🔧 常见问题

### Q: 服务经常被系统杀死怎么办？
A: 请在系统设置中：
1. 允许应用自启动
2. 关闭电池优化（设置页面有入口）
3. 锁定后台进程

### Q: 如何查看运行日志？
A: 在应用内「日志」标签页查看，或通过 adb 拉取：
```bash
adb shell run-as com.lanbing.smsforwarder cat files/sms_forwarder_logs.txt
```

### Q: 转发失败怎么办？
A: 检查：
1. Webhook 地址是否正确
2. 网络连接是否正常
3. 应用内日志查看详细错误

### Q: 如何查看隐私政策？
A: 在应用内「设置」页面的「隐私设置」卡片中点击「隐私政策」按钮。

### Q: 如何撤销隐私政策同意？
A: 在应用内「设置」页面的「隐私设置」卡片中点击「撤销隐私政策同意」按钮，系统会清除同意状态并退出应用。

### Q: 应用会收集我的短信内容吗？
A: 不会。所有数据仅存储在您的手机本地，不会上传到任何服务器。短信内容仅在转发时临时处理，转发目标由您自己配置。

### Q: 双卡手机如何区分哪个号码收到的短信？
A: 应用会自动识别接收短信的 SIM 卡，并在转发消息中显示本机号码。如果无法自动获取，您可以在设置页面手动输入自定义 SIM 号码。

### Q: 如何测试关键词规则？
A: 在设置页面点击「测试规则」按钮，输入测试短信内容，应用会显示匹配的规则和目标通道。

### Q: 如何在多个设备间同步配置？
A: 使用二维码导入导出功能：在源设备的设置页面点击「导出配置」生成二维码并截图，然后在目标设备的设置页面点击「扫码导入」扫描二维码即可完成配置迁移。

---

## 📄 许可证

本项目采用 [MIT 许可证](LICENSE.txt)。

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

提交 PR 时请说明：
- 变更目的与实现要点
- 隐私/合规影响（若处理敏感数据）
- 测试设备/Android 版本

---


<div align="center">

如果这个项目对你有帮助，欢迎给个 ⭐ Star！

</div>
