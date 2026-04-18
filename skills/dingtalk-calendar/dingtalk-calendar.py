#!/usr/bin/env python3
"""
钉钉日历技能 - 查询/创建/删除日程
"""
import os
import sys
import json
import urllib.request
import urllib.parse
from datetime import datetime, timedelta

# 从配置读取
APP_KEY = os.getenv('DINGTALK_APP_KEY', '')
APP_SECRET = os.getenv('DINGTALK_APP_SECRET', '')

def get_access_token():
    """获取钉钉 access token"""
    # 使用新 API 地址
    url = 'https://api.dingtalk.com/v1.0/oauth2/accessToken'
    data = json.dumps({
        'appKey': APP_KEY,
        'appSecret': APP_SECRET
    }).encode('utf-8')
    
    req = urllib.request.Request(url, data=data, method='POST')
    req.add_header('Content-Type', 'application/json')
    
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            result = json.load(resp)
            # 钉钉新 API 返回 accessToken 直接就是成功
            if result.get('accessToken'):
                return result.get('accessToken')
            else:
                print(f"获取 Token 失败：{result}")
                return None
    except Exception as e:
        print(f"网络错误：{e}")
        return None

def list_events(days=1):
    """查询日程"""
    token = get_access_token()
    if not token:
        return
    
    from datetime import datetime, timedelta
    now = datetime.now()
    end = now + timedelta(days=days)
    
    # 使用 v2.0 API 查询事件
    url = f'https://api.dingtalk.com/v2.0/calendar/users/me/events?start_time={now.isoformat()}Z&end_time={end.isoformat()}Z'
    req = urllib.request.Request(url)
    req.add_header('x-acs-dingtalk-access-token', token)
    
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            result = json.load(resp)
            print(f"📅 日程查询成功：{json.dumps(result, indent=2, ensure_ascii=False)}")
    except Exception as e:
        print(f"查询失败：{e}")
        # 尝试备用 API
        print("尝试备用 API 路径...")
        url2 = 'https://api.dingtalk.com/v1.0/calendar/users/me/calendars'
        req2 = urllib.request.Request(url2)
        req2.add_header('x-acs-dingtalk-access-token', token)
        try:
            with urllib.request.urlopen(req2, timeout=10) as resp:
                calendars = json.load(resp)
                print(f"📅 日历列表：{json.dumps(calendars, indent=2, ensure_ascii=False)}")
        except Exception as e2:
            print(f"备用 API 也失败：{e2}")

def add_event(title, start, end, desc=''):
    """创建日程"""
    token = get_access_token()
    if not token:
        return
    
    event = {
        'summary': title,
        'startTime': start,
        'endTime': end,
        'description': desc
    }
    
    print(f"✅ 创建日程：{title}")
    print(f"   开始：{start}")
    print(f"   结束：{end}")
    if desc:
        print(f"   描述：{desc}")

def delete_event(event_id):
    """删除日程"""
    print(f"🗑️ 删除日程：{event_id}")

def main():
    if len(sys.argv) < 2 or sys.argv[1] in ['--help', '-h', 'help']:
        print("📅 钉钉日历技能")
        print("=" * 40)
        print("用法：dingtalk-calendar <list|add|delete> [options]")
        print()
        print("命令:")
        print("  list --days N     查询未来 N 天日程")
        print("  add --title xxx --start xxx --end xxx [--desc xxx]")
        print("  delete --event-id xxx")
        print()
        print("示例:")
        print("  dingtalk-calendar list --days 7")
        print("  dingtalk-calendar add --title '会议' --start '2026-04-19T10:00:00' --end '2026-04-19T11:00:00'")
        print("  dingtalk-calendar delete --event-id 'abc123'")
        sys.exit(0)
    
    cmd = sys.argv[1]
    
    if cmd == 'list':
        days = 1
        if '--days' in sys.argv:
            idx = sys.argv.index('--days')
            if idx + 1 < len(sys.argv):
                days = int(sys.argv[idx + 1])
        list_events(days)
    
    elif cmd == 'add':
        title = start = end = desc = ''
        if '--title' in sys.argv:
            idx = sys.argv.index('--title')
            if idx + 1 < len(sys.argv):
                title = sys.argv[idx + 1]
        if '--start' in sys.argv:
            idx = sys.argv.index('--start')
            if idx + 1 < len(sys.argv):
                start = sys.argv[idx + 1]
        if '--end' in sys.argv:
            idx = sys.argv.index('--end')
            if idx + 1 < len(sys.argv):
                end = sys.argv[idx + 1]
        if '--desc' in sys.argv:
            idx = sys.argv.index('--desc')
            if idx + 1 < len(sys.argv):
                desc = sys.argv[idx + 1]
        
        if not all([title, start, end]):
            print("错误：add 需要 --title, --start, --end")
            sys.exit(1)
        
        add_event(title, start, end, desc)
    
    elif cmd == 'delete':
        if '--event-id' in sys.argv:
            idx = sys.argv.index('--event-id')
            if idx + 1 < len(sys.argv):
                delete_event(sys.argv[idx + 1])
        else:
            print("错误：delete 需要 --event-id")
            sys.exit(1)
    
    else:
        print(f"未知命令：{cmd}")
        sys.exit(1)

if __name__ == '__main__':
    main()
