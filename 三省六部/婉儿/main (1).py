#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
钉钉 OAuth 配置示例脚本

本示例仅用于演示：
1. 在代码层面如何对应“安全设置”中的回调地址（redirect_uri）。
2. 如何构造用户授权链接。
3. 在回调接口中如何接收 authCode，并演示后端换取 access_token 的逻辑骨架。

注意：
- 本代码不会真实调用钉钉接口，也不会包含任何真实密钥。
- 你需要在钉钉开发者后台 -> 应用详情 -> 开发配置 -> 安全设置 中，
  将本代码中的 redirect_uri 对应的域名/路径配置进去，且必须保持一致。
"""

import urllib3
urllib3.disable_warnings()

from dataclasses import dataclass
from typing import Optional
from urllib.parse import urlencode

import requests
from flask import Flask, request, jsonify, redirect


@dataclass
class DingTalkOAuthConfig:
    """钉钉 OAuth 配置，对应开发者后台的安全设置。"""

    app_key: str              # 对应 AppKey / SuiteKey
    app_secret: str           # 对应 AppSecret / SuiteSecret
    redirect_uri: str         # 需要在“安全设置”中配置的回调地址
    scope: str = "openid"     # 示例 scope，可按需调整

    auth_base_url: str = "https://login.dingtalk.com/oauth2/auth"  # 用户授权页面
    token_url: str = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken"  # 换取 access_token 接口（示例）


def build_oauth_authorize_url(config: DingTalkOAuthConfig, state: str) -> str:
    """构造钉钉 OAuth 授权链接。

    注意：redirect_uri 必须和钉钉后台“安全设置”中的配置完全一致。
    """

    params = {
        "client_id": config.app_key,
        "redirect_uri": config.redirect_uri,
        "response_type": "code",  # OAuth2 标准字段
        "scope": config.scope,
        "state": state,
    }
    return f"{config.auth_base_url}?{urlencode(params)}"


def exchange_code_for_token(config: DingTalkOAuthConfig, auth_code: str) -> dict:
    """使用后端服务端接口，用 authCode 换取 access_token。

    实际调用时：
    - 需要将 app_key / app_secret 作为凭证，并且注意妥善保管。
    - 本示例使用 verify=False 禁用 SSL 证书校验，仅作演示用，不建议在生产环境使用。
    """

    payload = {
        "clientId": config.app_key,
        "clientSecret": config.app_secret,
        "code": auth_code,
        "grantType": "authorization_code",
    }

    # 注意：verify=False 根据题目要求禁用证书校验
    resp = requests.post(config.token_url, json=payload, timeout=10, verify=False)
    try:
        resp.raise_for_status()
    except requests.HTTPError as exc:
        return {"error": str(exc), "status_code": resp.status_code, "body": resp.text}

    return resp.json()


# ============ Flask 示例应用 ============
app = Flask(__name__)

# 这里使用示例配置，实际请从环境变量或配置文件读取。
OAUTH_CONFIG = DingTalkOAuthConfig(
    app_key="YOUR_APP_KEY",      # TODO: 替换为真实 AppKey
    app_secret="YOUR_APP_SECRET",  # TODO: 替换为真实 AppSecret
    # 假设你在钉钉后台“安全设置”中配置的回调地址为：https://your-domain.com/oauth/callback
    # 那么这里 redirect_uri 也必须完全一致。
    redirect_uri="https://your-domain.com/oauth/callback",
)


@app.route("/")
def index():
    """首页：给出一个跳转到钉钉授权页的链接。"""

    # state 可以是防 CSRF 的随机字符串，这里仅做演示
    state = "demo_state_123"
    auth_url = build_oauth_authorize_url(OAUTH_CONFIG, state)
    return jsonify({
        "message": "点击前端按钮时，可跳转到此 URL 进行钉钉 OAuth 授权",
        "auth_url": auth_url,
        "tips": "请确保 redirect_uri 已在钉钉开发者后台->应用详情->开发配置->安全设置 中配置",
    })


@app.route("/oauth/callback")
def oauth_callback():
    """钉钉 OAuth 回调接口示例。

    - 钉钉在用户完成授权后，会携带 code（或 authCode）等参数回调到此地址。
    - 你需要在这里读取参数，然后调用后端接口换取 access_token。
    """

    # 钉钉文档中字段名可能为 code / authCode，根据实际文档调整。
    auth_code: Optional[str] = request.args.get("code") or request.args.get("authCode")
    state = request.args.get("state")

    if not auth_code:
        return jsonify({"error": "missing auth code", "detail": "未从回调参数中获取到 code/authCode"}), 400

    token_data = exchange_code_for_token(OAUTH_CONFIG, auth_code)

    return jsonify({
        "state": state,
        "auth_code": auth_code,
        "token_response": token_data,
    })


if __name__ == "__main__":
    # 仅作为本地调试示例，生产环境请使用 WSGI / 反向代理等部署方式。
    # host="0.0.0.0" 方便容器或局域网访问。
    app.run(host="0.0.0.0", port=8000, debug=True)
