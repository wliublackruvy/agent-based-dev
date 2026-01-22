import subprocess
import json
import os
import re
import requests
from termcolor import colored
# 引用配置，确保模型型号和厂家解耦
from agents.config import AGENT_CONFIG, DEEPSEEK_API_KEY, DEEPSEEK_TIMEOUT

def call_llm_for_agent(agent_name: str, system_prompt: str, user_prompt: str, json_mode: bool = False) -> str:
    """
    根据 Agent 名称从 config.py 中读取配置，自动路由到对应的模型型号和厂家。
    """
    if agent_name not in AGENT_CONFIG:
        print(colored(f"❌ No config found for agent: {agent_name}", "red"))
        return ""
    
    config = AGENT_CONFIG[agent_name]
    provider = config.get("provider")
    model_id = config.get("model_id")
    
    # 执行调用
    return call_llm(system_prompt, user_prompt, model_type=provider, model_id=model_id, json_mode=json_mode)

def call_llm(system_prompt: str, user_prompt: str, model_type: str = "qwen", model_id: str = None, json_mode: bool = False) -> str:
    """
    底层统一调用接口
    model_type: "qwen", "deepseek", "codex"
    model_id: 具体的模型型号，例如 "gpt-4o", "deepseek-chat", "qwen-coder-turbo"
    """
    
    # 组合 Prompt
    full_prompt = f"### System ###\n{system_prompt}\n\n### User ###\n{user_prompt}"
    if json_mode:
        full_prompt += "\n\nIMPORTANT: Output valid JSON only. Do not wrap in markdown blocks."

    # 根据厂家路由
    if model_type == "qwen":
        return _call_qwen_cli(full_prompt, model_id)
    elif model_type == "deepseek":
        return _call_deepseek_api(system_prompt, user_prompt, model_id)
    elif model_type == "codex":
        return _call_codex_cli(full_prompt, model_id)
    else:
        print(colored(f"❌ Unknown provider type: {model_type}", "red"))
        return ""

def _call_qwen_cli(prompt: str, model_id: str) -> str:
    """调用 Qwen CLI (One-shot 模式)"""
    model = model_id or "qwen-coder-turbo"
    print(colored(f"🤖 Calling Qwen CLI (Model: {model})...", "cyan"))
    try:
        # -y: YOLO模式自动接受建议
        # -m: 指定具体模型型号
        # -p: 传递 Prompt
        cmd = ["qwen", "-y", "-m", model, "-p", prompt]
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        stdout, stderr = process.communicate()
        if process.returncode != 0:
            print(colored(f"❌ Qwen Error: {stderr}", "red"))
            return ""
        return stdout.strip()
    except FileNotFoundError:
        print(colored("❌ Error: 'qwen' command not found in PATH.", "red"))
        return ""

def _call_deepseek_api(system: str, user: str, model_id: str) -> str:
    """通过 API 调用 DeepSeek"""
    if not DEEPSEEK_API_KEY:
        print(colored("❌ Error: DEEPSEEK_API_KEY not set in environment.", "red"))
        return ""
    
    model = model_id or "deepseek-chat"
    print(colored(f"🤖 Calling DeepSeek API (Model: {model})...", "magenta"))
    try:
        url = "https://api.deepseek.com/chat/completions"
        headers = {"Authorization": f"Bearer {DEEPSEEK_API_KEY}", "Content-Type": "application/json"}
        payload = {
            "model": model,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user}
            ],
            "stream": False
        }
        response = requests.post(url, headers=headers, json=payload, timeout=DEEPSEEK_TIMEOUT)
        response.raise_for_status()
        return response.json()["choices"][0]["message"]["content"]
    except Exception as e:
        print(colored(f"❌ DeepSeek API Error: {e}", "red"))
        return ""

def _call_codex_cli(prompt: str, model_id: str) -> str:
    """调用 Codex CLI (使用 exec 命令)"""
    fallback_model = "gpt-5-codex"
    if not model_id:
        print(colored(f"⚠️ reviewer 未在 config.py 中配置 model_id，默认回退为 {fallback_model}", "magenta"))
    model = model_id or fallback_model
    
    try:
        # --full-auto: 跳过所有确认
        # '-': 从 stdin 读取 prompt
        cmd = ["codex", "exec", "--full-auto"]
        
        # GLM Profile Support
        if model.lower() == "glm":
            print(colored(f"🤖 Calling Codex CLI (Profile: glm)...", "cyan"))
            cmd.extend(["--profile", "glm"])
        else:
            print(colored(f"🤖 Calling Codex CLI (Model: {model})...", "cyan"))
            cmd.extend(["-m", model])
            
        cmd.append("-")

        process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        stdout, stderr = process.communicate(input=prompt)
        
        if process.returncode != 0:
            # 捕获类似模型不支持或账号权限的错误
            if "Bad Request" in stderr:
                print(colored(f"❌ Codex API Error: {stderr}", "red"))
            else:
                print(colored(f"❌ Codex CLI Error: {stderr}", "red"))
            return ""
        return stdout.strip()
    except FileNotFoundError:
        print(colored("❌ Error: 'codex' command not found in PATH.", "red"))
        return ""

def parse_json_response(response_text: str):
    """
    通用 JSON 解析，处理多种包裹情况
    """
    if not response_text:
        return None

    text = response_text.strip()

    # 1) 优先提取 ```json ... ``` 代码块
    fence_pattern = re.compile(r"```(?:json)?\s*(\{.*?\})\s*```", re.DOTALL | re.IGNORECASE)
    candidates = fence_pattern.findall(text)

    # 2) 如果没有代码块，尝试通过第一个 { 和最后一个 } 截取
    if not candidates:
        start = text.find("{")
        end = text.rfind("}")
        if start != -1 and end != -1 and end > start:
            candidates.append(text[start:end + 1])
        else:
            candidates.append(text)

    for candidate in candidates:
        try:
            return json.loads(candidate.strip())
        except json.JSONDecodeError:
            continue

    print(colored("❌ JSON Parse Error. Raw output printed below:", "red"))
    print(response_text)
    return None
