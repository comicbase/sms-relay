-- 先执行此迁移，再升级 Android。保持可空以兼容旧版 App 和历史短信。
-- 不修改 RLS；号码由客户端在接收时按卡槽配置快照写入。
ALTER TABLE public.sms_messages ADD COLUMN IF NOT EXISTS recipient text;
COMMENT ON COLUMN public.sms_messages.recipient IS
    'Recipient phone number configured for the receiving SIM slot at receipt time; NULL if unknown.';
