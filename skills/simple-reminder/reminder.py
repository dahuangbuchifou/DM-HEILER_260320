#!/usr/bin/env python3
"""
简单日程提醒 - 本地存储 + 钉钉推送
"""
import os
import sys
import json
from datetime import datetime, timedelta

# 数据文件路径
DATA_FILE = os.path.expanduser('~/.openclaw/workspace/memory/schedule.json')

def load_schedule():
    """加载日程数据"""
    if not os.path.exists(DATA_FILE):
        return {'events': []}
    try:
        with open(DATA_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    except:
        return {'events': []}

def save_schedule(data):
    """保存日程数据"""
    os.makedirs(os.path.dirname(DATA_FILE), exist_ok=True)
    with open(DATA_FILE, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def add_event(title, start_time, desc=''):
    """创建日程"""
    data = load_schedule()
    event = {
        'id': len(data['events']) + 1,
        'title': title,
        'start_time': start_time,
        'description': desc,
        'created_at': datetime.now().isoformat(),
        'reminded': False
    }
    data['events'].append(event)
    save_schedule(data)
    print(f"✅ 日程已创建：{title}")
    print(f"   时间：{start_time}")
    if desc:
        print(f"   描述：{desc}")
    return event

def list_events(days=1):
    """查询日程"""
    data = load_schedule()
    now = datetime.now()
    end = now + timedelta(days=days)
    
    upcoming = []
    for evt in data['events']:
        try:
            # 支持多种时间格式
            time_str = evt['start_time']
            if 'T' in time_str:
                evt_time = datetime.fromisoformat(time_str.replace('Z', '').replace('+00:00', '').replace('+08:00', ''))
            else:
                evt_time = datetime.strptime(time_str, '%Y-%m-%d %H:%M')
            
            if now <= evt_time <= end:
                upcoming.append(evt)
        except Exception as e:
            print(f"调试：解析失败 {evt['start_time']} - {e}")
            continue
    
    # 按时间排序
    upcoming.sort(key=lambda x: x['start_time'])
    
    if not upcoming:
        print(f"📅 未来 {days} 天没有日程安排～")
        return
    
    print(f"📅 未来 {days} 天共 {len(upcoming)} 条日程：")
    print("=" * 50)
    for evt in upcoming:
        status = "✅ 已提醒" if evt.get('reminded') else "⏰ 待提醒"
        print(f"[{evt['id']}] {evt['title']}")
        print(f"    时间：{evt['start_time']}")
        if evt.get('description'):
            print(f"    描述：{evt['description']}")
        print(f"    状态：{status}")
        print()

def delete_event(event_id):
    """删除日程"""
    data = load_schedule()
    for i, evt in enumerate(data['events']):
        if evt['id'] == event_id:
            removed = data['events'].pop(i)
            save_schedule(data)
            print(f"🗑️ 已删除日程：{removed['title']}")
            return
    print(f"❌ 未找到日程 ID: {event_id}")

def mark_reminded(event_id):
    """标记为已提醒"""
    data = load_schedule()
    for evt in data['events']:
        if evt['id'] == event_id:
            evt['reminded'] = True
            save_schedule(data)
            print(f"✅ 已标记为已提醒：{evt['title']}")
            return
    print(f"❌ 未找到日程 ID: {event_id}")

def check_reminders():
    """检查待提醒的日程（用于定时任务）"""
    data = load_schedule()
    now = datetime.now()
    reminded = []
    
    for evt in data['events']:
        if evt.get('reminded'):
            continue
        try:
            evt_time = datetime.fromisoformat(evt['start_time'].replace('Z', '+00:00').replace('+08:00', ''))
            # 提前 15 分钟提醒
            if now >= evt_time - timedelta(minutes=15) and now <= evt_time:
                reminded.append(evt)
        except:
            continue
    
    if reminded:
        print(f"🔔 有待提醒的日程：{len(reminded)} 条")
        for evt in reminded:
            print(f"  - {evt['title']} ({evt['start_time']})")
    else:
        print("✅ 暂无待提醒日程")
    
    return reminded

def main():
    if len(sys.argv) < 2 or sys.argv[1] in ['--help', '-h', 'help']:
        print("📅 简单日程提醒")
        print("=" * 50)
        print("用法：reminder <command> [options]")
        print()
        print("命令:")
        print("  list --days N        查询未来 N 天日程")
        print("  add --title xxx --time 'YYYY-MM-DD HH:MM' [--desc xxx]")
        print("  delete --id N        删除日程")
        print("  check                检查待提醒日程")
        print()
        print("示例:")
        print("  reminder list --days 7")
        print("  reminder add --title '会议' --time '2026-04-20 14:00' --desc '项目讨论'")
        print("  reminder delete --id 1")
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
        title = start_time = desc = ''
        if '--title' in sys.argv:
            idx = sys.argv.index('--title')
            if idx + 1 < len(sys.argv):
                title = sys.argv[idx + 1]
        if '--time' in sys.argv:
            idx = sys.argv.index('--time')
            if idx + 1 < len(sys.argv):
                start_time = sys.argv[idx + 1]
        if '--desc' in sys.argv:
            idx = sys.argv.index('--desc')
            if idx + 1 < len(sys.argv):
                desc = sys.argv[idx + 1]
        
        if not all([title, start_time]):
            print("错误：add 需要 --title 和 --time")
            sys.exit(1)
        
        add_event(title, start_time, desc)
    
    elif cmd == 'delete':
        if '--id' in sys.argv:
            idx = sys.argv.index('--id')
            if idx + 1 < len(sys.argv):
                delete_event(int(sys.argv[idx + 1]))
        else:
            print("错误：delete 需要 --id")
            sys.exit(1)
    
    elif cmd == 'check':
        check_reminders()
    
    else:
        print(f"未知命令：{cmd}")
        sys.exit(1)

if __name__ == '__main__':
    main()
