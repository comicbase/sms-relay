# Security Policy

## 报告安全问题

请不要在公开 Issue 中披露真实短信、电话号码、账号凭据、数据库内容或可利用的安全细节。维护者发布专用安全联系方式后，请通过该渠道私下报告。

## 部署者安全清单

- 为 `sms_messages`、`devices` 等表启用并验证 Row Level Security。
- Android 和网页仅使用 Publishable Key，不使用 `service_role` 或 Secret Key。
- 不把 `.env.local`、`supabase.properties`、签名文件、Auth 密码或真机数据提交到仓库。
- 为每位用户和设备限制可查询、插入和更新的数据范围。
- 定期更新依赖，并立即撤销意外泄露的密钥或登录会话。

此示例项目不提供托管服务。使用者需要自行负责其 Supabase、Vercel、Android 设备和账号安全。

