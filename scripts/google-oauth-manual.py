#!/usr/bin/env python3
"""
Google OAuth 2.0 - 手动获取 Refresh Token（简化版）
不需要本地服务器，手动复制授权码即可
"""
import os
import sys
import json
import urllib.request
import urllib.parse

CLIENT_ID = '958710948002-rrbu696dq03q8dmleckq2joec4qq1nbn.apps.googleusercontent.com'
CLIENT_SECRET = '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'
REDIRECT_URI = 'urn:ietf:wg:oauth:2.0:oob'  # Out-of-band flow
SCOPES = [
    'https://www.googleapis.com/auth/calendar',
    'https://www.googleapis.com/auth/calendar.events',
    'https://www.googleapis.com/auth/gmail.readonly',
    'https://www.googleapis.com/auth/gmail.send',
]

def main():
    print("=" * 70)
    print("Google OAuth 2.0 - 手动获取 Refresh Token")
    print("=" * 70)
    print(f"\n📧 Google 账号：iamzjf01@gmail.com")
    print(f"📅 项目：OpenClaw-Calendar-260406")
    print(f"\n🔐 请求权限:")
    for scope in SCOPES:
        print(f"   - {scope}")
    
    # 构建授权 URL（使用 oob 模式，不需要本地服务器）
    auth_params = {
        'client_id': CLIENT_ID,
        'redirect_uri': REDIRECT_URI,
        'response_type': 'code',
        'scope': ' '.join(SCOPES),
        'access_type': 'offline',
        'prompt': 'consent',
    }
    auth_url = f"https://accounts.google.com/o/oauth2/v2/auth?{urllib.parse.urlencode(auth_params)}"
    
    print(f"\n{'='*70}")
    print("📋 操作步骤：")
    print(f"{'='*70}")
    print(f"1. 复制下方链接，在浏览器中打开")
    print(f"2. 登录 Google 账号并点击「允许」授权")
    print(f"3. 授权成功后，页面会显示一个授权码（4/ 开头的字符串）")
    print(f"4. 复制授权码，粘贴到下方提示处\n")
    
    print(f"🔗 授权链接：")
    print(f"{auth_url}\n")
    print(f"{'='*70}\n")
    
    # 获取授权码
    auth_code = input("请输入授权码：").strip()
    
    if not auth_code:
        print("❌ 未输入授权码，退出")
        sys.exit(1)
    
    print(f"\n⏳ 正在用授权码换取 Refresh Token...")
    
    # 用授权码换取 refresh token
    token_data = {
        'client_id': CLIENT_ID,
        'client_secret': CLIENT_SECRET,
        'code': auth_code,
        'grant_type': 'authorization_code',
        'redirect_uri': REDIRECT_URI,
    }
    
    req = urllib.request.Request(
        'https://oauth2.googleapis.com/token',
        data=urllib.parse.urlencode(token_data).encode(),
        method='POST'
    )
    req.add_header('Content-Type', 'application/x-www-form-urlencoded')
    
    try:
        with urllib.request.urlopen(req) as resp:
            result = json.load(resp)
            refresh_token = result.get('refresh_token')
            
            if refresh_token:
                # 写入配置文件
                env_path = os.path.expanduser('~/.config/google-calendar/secrets.env')
                
                # 读取现有配置
                lines = []
                if os.path.exists(env_path):
                    with open(env_path, 'r') as f:
                        lines = f.readlines()
                
                # 更新或添加 refresh token
                new_lines = []
                token_set = False
                for line in lines:
                    if line.startswith('export GOOGLE_REFRESH_TOKEN='):
                        new_lines.append(f'export GOOGLE_REFRESH_TOKEN={refresh_token}\n')
                        token_set = True
                    else:
                        new_lines.append(line)
                
                if not token_set:
                    new_lines.append(f'export GOOGLE_REFRESH_TOKEN={refresh_token}\n')
                
                with open(env_path, 'w') as f:
                    f.writelines(new_lines)
                
                print(f"\n{'='*70}")
                print("✅ Refresh Token 获取成功!")
                print(f"{'='*70}")
                print(f"📁 已保存到：{env_path}")
                print(f"\n📋 配置文件内容:")
                with open(env_path, 'r') as f:
                    print(f.read())
                print(f"\n🎉 配置完成！现在可以使用 Google Calendar 和 Gmail 了")
            else:
                print(f"❌ 未获取到 Refresh Token: {result}")
                sys.exit(1)
    except urllib.error.HTTPError as e:
        error_body = e.read().decode()
        print(f"❌ HTTP 错误 {e.code}: {error_body}")
        sys.exit(1)
    except Exception as e:
        print(f"❌ 错误：{e}")
        sys.exit(1)

if __name__ == '__main__':
    main()
