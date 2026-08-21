# SMS Relay

SMS Relay 是一个面向 Android 初学者的完整课程案例：Android 客户端接收运营商短信，在本地使用 Room 保存数据，并通过 WorkManager 上传到 Supabase；远程管理网页登录后可查询收到的短信。

## 项目结构

```text
sms-relay/
├── android/   # Kotlin + Jetpack Compose Android 客户端
└── web/       # React + Vite 远程短信管理网页
```

详细的环境配置、数据库结构、运行方法和故障排查请分别查看：

- [Android 客户端文档](android/README.md)
- [远程管理网页文档](web/README.md)

## 数据流程

```text
运营商 SMS
   ↓
Android SMS_RECEIVED BroadcastReceiver
   ↓
Room 本地数据库
   ↓
WorkManager 后台上传
   ↓
Supabase + Row Level Security
   ↓
React 远程管理网页
```

## 开始使用

1. 按照 Android 文档在 Supabase 创建表、Auth 用户和 RLS 策略。
2. 将 `android/supabase.properties.example` 复制为 `android/supabase.properties`，填写 Project URL 和 Publishable Key。
3. 构建并安装 Android 应用，授予短信权限。
4. 将 `web/.env.example` 复制为 `web/.env.local`，填写相同的公开客户端配置。
5. 启动网页，使用普通 Supabase Auth 用户登录。

网页也可以通过 Synology Web Station 部署到 NAS，详细步骤见网页项目文档。

## 特别注意

- 应用接收的是运营商传统 SMS。小米等手机启用“免费网络短信”“5G 消息”或 RCS 后，消息可能不会触发标准的 `SMS_RECEIVED` 广播；测试时可先关闭这些功能。
- 部分国产 Android 系统需要允许后台自启动，并将电池策略设为“不限制”。
- 本项目不是系统应用，也不需要设置为默认短信应用。
- 客户端只能使用 Supabase Project URL 与 Publishable Key，绝不能嵌入 `service_role`、Secret Key、数据库密码或 Auth 用户密码。
- 仓库不应包含真实短信、验证码、电话号码、真机数据库、日志或未经脱敏的截图。

## 合法与隐私

本项目仅用于学习及管理你本人拥有或已获明确授权的设备。短信可能包含验证码、身份信息和其他敏感数据。部署者有责任遵守当地法律、取得必要授权、配置严格的 RLS 策略，并妥善管理账号与数据。

## License

本项目使用 [MIT License](LICENSE)。
