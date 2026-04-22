#!/usr/bin/env python3
"""
Google OAuth 2.0 - 获取 Refresh Token
用于 Google Calendar 和 Gmail API 访问
"""
import os
import sys
import json
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import webbrowser
import urllib.request
import urllib.parse

# 从现有配置读取
CLIENT_ID = os.getenv('GOOGLE_CLIENT_ID', '958710948002-rrbu696dq03q8dmleckq2joec4qq1nbn.apps.googleusercontent.com')
CLIENT_SECRET = os.getenv('GOOGLE_CLIENT_SECRET', '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')
REDIRECT_URI = 'http://localhost:8085/callback'
SCOPES = [
    'https://www.googleapis.com/auth/calendar',
    'https://www.googleapis.com/auth/calendar.events',
    'https://www.googleapis.com/auth/gmail.readonly',
    'https://www.googleapis.com/auth/gmail.send',
]

class CallbackHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == '/callback':
            params = parse_qs(parsed.query)
            code = params.get('code', [None])[0]
            error = params.get('error', [None])[0]
            
            if error:
                self.send_response(200)
                self.send_header('Content-type', 'text/html')
                self.end_headers()
                self.wfile.write(f'<html><body><h1>授权失败</h1><p>{error}</p><p>请关闭窗口重试。</p></body></html>'.encode())
                sys.exit(1)
            
            if code:
                # 用 code 换取 refresh token
                token_data = {
                    'client_id': CLIENT_ID,
                    'client_secret': CLIENT_SECRET,
                    'code': code,
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
                            with open(env_path, 'r') as f:
                                lines = f.readlines()
                            
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
                            
                            self.send_response(200)
                            self.send_header('Content-type', 'text/html')
                            self.end_headers()
                            self.wfile.write(f'''
                                <html>
                                    <body style="font-family: Arial; text-align: center; padding: 50px;">
                                        <h1 style="color: green;">✓ 授权成功!</h1>
                                        <p>Refresh Token 已保存到配置文件。</p>
                                        <p>请关闭此窗口。</p>
                                    </body>
                                </html>
                            '''.encode())
                            print(f"\n✅ Refresh Token 获取成功!")
                            print(f"已保存到：{env_path}")
                        else:
                            print(f"❌ 未获取到 Refresh Token: {result}")
                            sys.exit(1)
                except Exception as e:
                    print(f"❌ 错误：{e}")
                    sys.exit(1)
            
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            self.wfile.write('<html><body><h1>请关闭窗口</h1></body></html>'.encode('utf-8'))
            sys.exit(0)
        else:
            self.send_response(404)
            self.end_headers()
    
    def log_message(self, format, *args):
        pass  # 静默

def main():
    print("=" * 60)
    print("Google OAuth 2.0 - 获取 Refresh Token")
    print("=" * 60)
    print(f"\n📧 Google 账号：iamzjf01@gmail.com")
    print(f"📅 项目：OpenClaw-Calendar-260406")
    print(f"\n🔐 请求权限:")
    for scope in SCOPES:
        print(f"   - {scope}")
    print(f"\n🌐 将在浏览器中打开授权页面...")
    print(f"   如未自动打开，请手动访问下方链接\n")
    
    # 构建授权 URL
    auth_params = {
        'client_id': CLIENT_ID,
        'redirect_uri': REDIRECT_URI,
        'response_type': 'code',
        'scope': ' '.join(SCOPES),
        'access_type': 'offline',
        'prompt': 'consent',  # 强制获取 refresh token
    }
    auth_url = f"https://accounts.google.com/o/oauth2/v2/auth?{urllib.parse.urlencode(auth_params)}"
    
    print(f"\n🔗 授权链接:\n{auth_url}\n")
    
    # 尝试打开浏览器
    try:
        webbrowser.open(auth_url)
    except:
        pass
    
    # 启动本地服务器监听回调
    print(f"\n⏳ 等待授权回调 (监听 {REDIRECT_URI})...")
    print(f"   请在浏览器中登录 Google 账号并授权\n")
    
    server = HTTPServer(('localhost', 8085), CallbackHandler)
    server.handle_request()  # 只处理一次请求

if __name__ == '__main__':
    main()
